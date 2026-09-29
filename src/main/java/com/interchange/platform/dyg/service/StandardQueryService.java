package com.interchange.platform.dyg.service;

import com.interchange.platform.dyg.entity.StandardBankAccView;
import com.interchange.platform.dyg.entity.StandardBankInputView;
import com.interchange.platform.dyg.entity.StandardCorpView;
import com.interchange.platform.dyg.entity.StandardCurView;
import com.interchange.platform.dyg.entity.StandardExternalCorpAccView;
import com.interchange.platform.dyg.entity.StandardExternalCorpView;
import com.interchange.platform.dyg.entity.StandardItemView;
import com.interchange.platform.dyg.entity.StandardRegionView;
import com.interchange.platform.dyg.entity.StandardUserView;
import com.interchange.platform.standard.core.base.BaseDao;
import com.interchange.platform.standard.utils.DateUtil;
import com.interchange.platform.standard.utils.JsonUtil;
import com.interchange.platform.standard.utils.StringUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准接口的 9 个查询接口（币种 / 单位 / 用户 / 省市 / 银行账号 / 网点 / 客商 / 客商账号 / 科目）。
 *
 * <p>查询按传入字段动态拼 HQL，空值字段自动跳过：
 * 字符串默认模糊匹配，日期用大于等于。返回码沿用原系统：status = 2 成功、1 失败。
 */
@Service
@Transactional(readOnly = true)
public class StandardQueryService {

    private static final Logger log = LoggerFactory.getLogger(StandardQueryService.class);

    /** status：成功。取自 dyg StandardConfig.StatusS（2 成功、1 失败，别按常识改） */
    private static final String STATUS_OK = "2";
    private static final String STATUS_FAIL = "1";

    @Resource
    private BaseDao dao;


    /* ===================== 9 个查询接口 ===================== */

    /** 币种：curCode=、curName/englishCode 模糊、updateDate 大于等于 */
    public Map<String, Object> cur(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        if (!validDate(p.get("updateDate"))) {
            return fail("更新日期格式错误，应为 yyyy-MM-dd");
        }
        return ok(query(StandardCurView.class, p,
                like("curName"), like("englishCode"), eq("curCode"), ge("updateDate")));
    }

    /** 单位：code=、name 模糊、updateDate 大于等于 */
    public Map<String, Object> corp(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        return ok(query(StandardCorpView.class, p, eq("code"), like("name"), ge("updateDate")));
    }

    /** 用户：单位编码/名称/登录名精确、姓名模糊、updateDate 大于等于 */
    public Map<String, Object> user(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        // 单位名称沿用原实现为精确匹配（不是模糊）
        return ok(query(StandardUserView.class, p,
                eq("corpCode"), eq("corpName"), eq("loginName"), like("username"), ge("updateDate")));
    }

    /**
     * 省市：读 SYS_REGION 基表（原实现这一支没有走视图）。
     * 四个条件至少填一个，否则判失败，与原实现一致。
     */
    public Map<String, Object> region(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        if (StringUtil.isBlank(p.get("cityCode")) && StringUtil.isBlank(p.get("cityName"))
                && StringUtil.isBlank(p.get("provCode")) && StringUtil.isBlank(p.get("provName"))) {
            return fail("市代码、市名称、省代码、省名称至少填写一个");
        }
        return ok(query(StandardRegionView.class, p,
                eq("bankInputCity"), like("name"), eq("bankProv"), like("provInputName")));
    }

    /** 银行账号：账号/单位编码精确、账户名/单位名称模糊、updateDate 大于等于 */
    public Map<String, Object> bankAcc(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        return ok(query(StandardBankAccView.class, p,
                eq("bankAcc"), like("accName"), eq("corpCode"), like("corpName"), ge("updateDate")));
    }

    /** 银行网点（联行号）：bankCode=、bankName 模糊、updateDate 大于等于 */
    public Map<String, Object> bankInput(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        return ok(query(StandardBankInputView.class, p,
                eq("bankCode"), like("bankName"), ge("updateDate")));
    }

    /** 客商：code=、name 模糊 */
    public Map<String, Object> externalCorp(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        return ok(query(StandardExternalCorpView.class, p, eq("code"), like("name")));
    }

    /** 客商银行账号：code=/externalAcc=、name 模糊 */
    public Map<String, Object> externalCorpAcc(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        return ok(query(StandardExternalCorpAccView.class, p,
                eq("code"), like("name"), eq("externalAcc")));
    }

    /** 科目：itemCode=、itemName 模糊、updateDate 大于等于 */
    public Map<String, Object> item(String body) {
        Map<String, String> p = parse(body);
        if (p == null) {
            return emptyParam();
        }
        return ok(query(StandardItemView.class, p,
                eq("itemCode"), like("itemName"), ge("updateDate")));
    }

    /* ===================== 条件与查询 ===================== */

    /** 一个查询条件：报文里的哪个字段、怎么比较、映射到实体的哪个属性 */
    private record Cond(String param, String prop, Op op) {
    }

    private enum Op { EQ, LIKE, GE }

    private static Cond eq(String prop) {
        return new Cond(prop, prop, Op.EQ);
    }

    private static Cond like(String prop) {
        return new Cond(prop, prop, Op.LIKE);
    }

    private static Cond ge(String prop) {
        return new Cond(prop, prop, Op.GE);
    }

    /**
     * 动态拼 HQL 并取数。条件全部为空时不加 where，等于全表查（与原实现一致）。
     * 结果按实体的字段声明顺序转成 Map，字段名即响应字段名。
     */
    private <T> List<Map<String, Object>> query(Class<T> type, Map<String, String> params, Cond... conds) {
        StringBuilder hql = new StringBuilder("from ").append(type.getSimpleName());
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        for (Cond c : conds) {
            String v = params.get(c.param());
            if (StringUtil.isBlank(v)) {
                continue;
            }
            where.append(where.isEmpty() ? " where " : " and ");
            // 位置参数从 0 开始（Hibernate 6）
            where.append(c.prop()).append(switch (c.op()) {
                case LIKE -> " like ?" + args.size();
                case GE -> " >= ?" + args.size();
                default -> " = ?" + args.size();
            });
            args.add(c.op() == Op.LIKE ? "%" + v.trim() + "%" : v.trim());
        }
        List<T> rows = args.isEmpty()
                ? dao.list(hql.toString())
                : dao.list(hql.append(where).toString(), args.toArray());
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (T row : rows) {
            result.add(toMap(row));
        }
        return result;
    }

    /** 实体 → Map，字段名即响应字段名 */
    private Map<String, Object> toMap(Object entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                try {
                    Method getter = entity.getClass().getMethod(
                            "get" + Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1));
                    map.put(f.getName(), getter.invoke(entity));
                } catch (Exception e) {
                    log.debug("读取属性失败: {}", f.getName());
                }
            }
        }
        return map;
    }

    /* ===================== 报文与响应 ===================== */

    /** 报文解析：入参是一整段 JSON，字段缺失即视为空 */
    private Map<String, String> parse(String body) {
        if (StringUtil.isBlank(body)) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = JsonUtil.toMap(body);
            Map<String, String> p = new LinkedHashMap<>();
            raw.forEach((k, v) -> p.put(k, v == null ? null : String.valueOf(v)));
            return p;
        } catch (Exception e) {
            log.warn("标准查询接口入参解析失败：{}", e.getMessage());
            return null;
        }
    }

    private boolean validDate(String value) {
        return DateUtil.isValidDate(value);
    }

    private static Map<String, Object> ok(List<Map<String, Object>> rows) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", STATUS_OK);
        r.put("message", "查询成功");
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
        return fail("报文为空或不是 JSON 对象");
    }
}
