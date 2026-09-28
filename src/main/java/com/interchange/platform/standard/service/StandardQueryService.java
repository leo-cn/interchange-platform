package com.interchange.platform.standard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interchange.platform.config.AppProps;
import com.interchange.platform.config.DataSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准接口服务脚本里的 9 个「查询类」接口（币种 / 单位 / 用户 / 省市 / 银行账号 /
 * 联行号 / 客商 / 客商账号 / 科目）。
 *
 * <p>对应 dyg-erp 的 {@code standard/controller/Standard*Web}，契约照抄：
 * <ul>
 *   <li>入参是<b>一段 JSON 报文</b>，非空字段作为查询条件；</li>
 *   <li>编码类条件用 {@code =}，名称类条件用 {@code like '%x%'}，{@code updateDate} 用 {@code >=}；</li>
 *   <li>出参固定三字段 {@code status / message / date}，且 <b>status=2 成功、1 失败</b>（反直觉，勿改）；</li>
 *   <li>{@code date} 是原系统的字段名拼写（不是 data），照抄以保证对方解析不出错。</li>
 * </ul>
 *
 * <p><b>两处刻意与原实现不同</b>：
 * <ul>
 *   <li>条件一律用 {@code ?} 绑定参数 —— 原实现是字符串拼接 HQL，存在注入风险；</li>
 *   <li>加了行数上限 {@link #MAX_ROWS} —— 原实现 {@code where 1=1} 无分页，
 *       一个不带条件的请求就能把整表拉回来。</li>
 * </ul>
 *
 * <p>读的是 {@code standard_*_view} 这些<b>视图</b>（省市那个直接读 SYS_REGION 表），
 * 不是基表；视图不存在会在启动自检里报出来。
 */
@Service
public class StandardQueryService {

    private static final Logger log = LoggerFactory.getLogger(StandardQueryService.class);

    /** status：成功。取自 dyg StandardConfig.StatusS（2成功、1失败，别按常识改） */
    private static final String STATUS_OK = "2";
    private static final String STATUS_FAIL = "1";

    /** 单次查询最多返回多少行；超出只取前 N 行并打日志 */
    private static final int MAX_ROWS = 1000;

    /**
     * 视图里"更新日期"这一列的列名。
     * 原实体 {@code updateDate} 没有 @Column，JPA 默认列名就是 updateDate；
     * 若 MySQL 侧视图实际用的是 update_date，只改这一个常量即可。
     */
    private static final String COL_UPDATE_DATE = "updateDate";

    private final AppProps appProps;
    private final DataSourceRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    public StandardQueryService(AppProps appProps, DataSourceRegistry registry) {
        this.appProps = appProps;
        this.registry = registry;
    }

    // ==================================================================
    //  9 个查询接口
    // ==================================================================

    /** 币种：curCode=、curName/englishCode 模糊、updateDate 大于等于 */
    public Map<String, Object> cur(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        if (!validDate(p.get("updateDate"))) {
            return fail("更新日期格式错误，应为 yyyy-MM-dd");
        }
        Where w = new Where()
                .eq("cur_code", p.get("curCode"))
                .like("cur_name", p.get("curName"))
                .like("english_code", p.get("englishCode"))
                .ge(COL_UPDATE_DATE, p.get("updateDate"));
        return ok(run("standard_cur_view",
                cols("cur_code", "cur_name", "english_code", "cur_exrate", "valid_sign", COL_UPDATE_DATE),
                names("curCode", "curName", "englishCode", "curExrate", "validSign", "updateDate"), w));
    }

    /** 单位：code=、name 模糊、updateDate 大于等于 */
    public Map<String, Object> corp(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        Where w = new Where()
                .eq("code", p.get("code"))
                .like("name", p.get("name"))
                .ge(COL_UPDATE_DATE, p.get("updateDate"));
        return ok(run("standard_corp_view",
                cols("code", "name", "status", COL_UPDATE_DATE),
                names("code", "name", "status", "updateDate"), w));
    }

    /** 用户：单位编码/名称/登录名精确、姓名模糊、updateDate 大于等于 */
    public Map<String, Object> user(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        Where w = new Where()
                .eq("corpCode", p.get("corpCode"))
                // 原实现单位名称也是精确匹配（不是模糊），保持一致
                .eq("corpName", p.get("corpName"))
                .eq("loginName", p.get("loginName"))
                .like("username", p.get("username"))
                .ge(COL_UPDATE_DATE, p.get("updateDate"));
        return ok(run("STANDARD_USER_VIEW",
                cols("loginName", "username", "corpCode", "corpName", "status", COL_UPDATE_DATE),
                names("loginName", "username", "corpCode", "corpName", "status", "updateDate"), w));
    }

    /**
     * 省市：直接读 SYS_REGION 表（原实现这一支没有走视图）。
     * 原实现要求四个条件至少填一个，否则判失败，这里保持一致。
     */
    public Map<String, Object> region(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        if (blank(p.get("cityCode")) && blank(p.get("cityName"))
                && blank(p.get("provCode")) && blank(p.get("provName"))) {
            return fail("市代码、市名称、省代码、省名称至少填写一个");
        }
        Where w = new Where()
                .eq("bank_input_city", p.get("cityCode"))
                .like("name", p.get("cityName"))
                .eq("bank_prov", p.get("provCode"))
                .like("prov_input_name", p.get("provName"));
        return ok(run("SYS_REGION",
                cols("bank_input_city", "name", "bank_prov", "prov_input_name"),
                names("cityCode", "cityName", "provCode", "provName"), w));
    }

    /** 银行账号：账号/单位编码精确、账户名/单位名称模糊、updateDate 大于等于 */
    public Map<String, Object> bankAcc(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        Where w = new Where()
                .eq("bankAcc", p.get("bankAcc"))
                .like("accName", p.get("accName"))
                .eq("corpCode", p.get("corpCode"))
                .like("corpName", p.get("corpName"))
                .ge(COL_UPDATE_DATE, p.get("updateDate"));
        return ok(run("STANDARD_BANKACC_VIEW",
                cols("bankAcc", "accName", "corpCode", "corpName", "curCode", "curName",
                        "bankCode", "bankName", "isOnline", "validSign", COL_UPDATE_DATE),
                names("bankAcc", "accName", "corpCode", "corpName", "curCode", "curName",
                        "bankCode", "bankName", "isOnline", "validSign", "updateDate"), w));
    }

    /** 银行网点（联行号）：bankCode=、bankName 模糊、updateDate 大于等于 */
    public Map<String, Object> bankInput(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        Where w = new Where()
                .eq("bankCode", p.get("bankCode"))
                .like("bankName", p.get("bankName"))
                .ge(COL_UPDATE_DATE, p.get("updateDate"));
        return ok(run("STANDARD_BANKINPUT_VIEW",
                cols("bankCode", "bankName", "cityCode", "cityName", "provCode", "provName", COL_UPDATE_DATE),
                names("bankCode", "bankName", "cityCode", "cityName", "provCode", "provName", "updateDate"), w));
    }

    /** 客商：code=、name 模糊 */
    public Map<String, Object> externalCorp(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        Where w = new Where()
                .eq("code", p.get("code"))
                .like("name", p.get("name"));
        return ok(run("standard_externalcorp_view",
                cols("code", "name", "externalType", "bwType", "status"),
                names("code", "name", "externalType", "bwType", "status"), w));
    }

    /** 客商银行账号：code=/externalAcc=、name 模糊 */
    public Map<String, Object> externalCorpAcc(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        Where w = new Where()
                .eq("code", p.get("code"))
                .like("name", p.get("name"))
                .eq("externalAcc", p.get("externalAcc"));
        return ok(run("standard_externalcorp_acc_view",
                cols("code", "name", "externalType", "externalAcc", "bankCode", "bankName"),
                names("code", "name", "externalType", "externalAcc", "bankCode", "bankName"), w));
    }

    /** 科目：itemCode=、itemName 模糊、updateDate 大于等于 */
    public Map<String, Object> item(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        Where w = new Where()
                .eq("item_code", p.get("itemCode"))
                .like("item_name", p.get("itemName"))
                .ge(COL_UPDATE_DATE, p.get("updateDate"));
        return ok(run("standard_item_view",
                cols("item_code", "item_name", COL_UPDATE_DATE),
                names("itemCode", "itemName", "updateDate"), w));
    }

    // ==================================================================
    //  执行与拼装
    // ==================================================================

    /**
     * 查视图。
     *
     * <p>取值<b>按下标</b>而不是列名：Oracle 会把列名返回成大写、MySQL 保持原样，
     * 按列名取会在换库时悄悄失效；按下标取则与库无关。
     */
    private List<Map<String, Object>> run(String view, String[] cols, String[] names, Where w) {
        String sql = "select " + String.join(", ", cols) + " from " + view + " where 1=1" + w.sql();
        JdbcTemplate t = new JdbcTemplate(jdbc().getDataSource());
        // 驱动层限流，不动 SQL，MySQL/Oracle 都生效
        t.setMaxRows(MAX_ROWS + 1);
        return t.query(sql, rs -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            int n = 0;
            while (rs.next()) {
                if (++n > MAX_ROWS) {
                    log.warn("标准查询接口返回超过 {} 行，已截断：{}", MAX_ROWS, view);
                    break;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < names.length; i++) {
                    row.put(names[i], rs.getString(i + 1));
                }
                rows.add(row);
            }
            return rows;
        }, w.args.toArray());
    }

    /**
     * 条件拼装。一律使用绑定参数，不做字符串拼接。
     */
    private static final class Where {
        private final StringBuilder sql = new StringBuilder();
        private final List<Object> args = new ArrayList<>();

        Where eq(String column, String value) {
            return add(column, " = ?", value);
        }

        Where like(String column, String value) {
            return add(column, " like ?", value == null ? null : "%" + value + "%");
        }

        Where ge(String column, String value) {
            return add(column, " >= ?", value);
        }

        private Where add(String column, String op, String value) {
            if (!blank(value)) {
                sql.append(" and ").append(column).append(op);
                args.add(value);
            }
            return this;
        }

        String sql() {
            return sql.toString();
        }
    }

    private JdbcTemplate jdbc() {
        String key = appProps.getMdm().getDatasource();
        if (key == null || key.isBlank()) {
            key = DataSourceRegistry.MAIN;
        }
        return registry.template(key);
    }

    /** 报文解析：入参是一整段 JSON，字段缺失即视为空 */
    private Map<String, String> parse(String body) {
        if (blank(body)) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = mapper.readValue(body, Map.class);
            Map<String, String> p = new LinkedHashMap<>();
            raw.forEach((k, v) -> p.put(k, v == null ? null : String.valueOf(v)));
            return p;
        } catch (Exception e) {
            log.warn("标准查询接口入参解析失败：{}", e.getMessage());
            return null;
        }
    }

    private boolean validDate(String value) {
        if (blank(value)) {
            return true;
        }
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static Map<String, Object> ok(List<Map<String, Object>> rows) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", STATUS_OK);
        r.put("message", null);
        // 字段名就是原系统的 date（拼写照抄），别改成 data
        r.put("date", rows);
        return r;
    }

    private static Map<String, Object> fail(String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", STATUS_FAIL);
        r.put("message", message);
        r.put("date", null);
        return r;
    }

    private static Map<String, Object> emptyParam() {
        return fail("入参为空错误！");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String[] cols(String... c) {
        return c;
    }

    private static String[] names(String... n) {
        return n;
    }
}
