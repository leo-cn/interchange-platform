package com.interchange.platform.mdm;

import com.interchange.platform.config.AppProps;
import com.interchange.platform.config.DataSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动自检：确认 MDM 要写的表和列在目标库里都存在。
 *
 * <p><b>为什么需要它</b>：这套接口落的是资金系统的基表（SYS_CORP / BT_BANK_ACC 等），
 * 这些表的 CREATE TABLE 属于产品基线脚本，不在本仓库里；MD_ID 等字段又是后加的。
 * 少了任何一个列，报错会发生在半夜的主数据推送上，而且是一条意义不明的 SQL 语法错误。
 * 这里在启动时就把它变成一条人能看懂的告警。
 *
 * <p><b>只做检查，不做 DDL</b>：平台不拥有这些表，自动补列的风险远大于收益，缺什么手工补。
 *
 * <p>只支持 MySQL 的信息字典查询；其它库（例如 Oracle）会跳过并提示改用 PASSWORD 方式人工核对。
 */
@Component
public class MdmSchemaChecker {

    private static final Logger log = LoggerFactory.getLogger(MdmSchemaChecker.class);

    /** 每张表需要用到的列。改这组常量前，先确认 MdmReceiveService 里真的在用这些列 */
    private static final Map<String, String[]> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("BT_BANK_TYPE", cols("id", "md_id", "bank_type", "type_name", "bank_prefix",
                "valid_sign", "is_system", "create_date", "create_by", "update_date", "update_by"));
        REQUIRED.put("BIS_BIF_INIT", cols("id", "bif_code", "name", "bank_type_id",
                "valid_sign", "is_system", "create_date", "create_by"));
        REQUIRED.put("BT_INPUT_BANK_INFO", cols("id", "md_id", "sys_bank_code", "bank_name", "short_name",
                "bank_prefix", "bank_city_code", "valid_sign", "create_date", "create_by",
                "update_date", "update_by"));
        REQUIRED.put("SYS_CORP", cols("id", "md_id", "code", "name", "short_name", "name_en", "parent_md_id",
                "cur_id", "soc_code", "unit_attribute", "status", "group_code", "net_id", "type",
                "listed_company", "rat_group", "is_limit_quota", "create_time", "create_by", "use_account_code"));
        REQUIRED.put("BT_BANK_ACC", cols("id", "md_id", "name", "bank_acc", "acc_name", "corp_id", "acc_type",
                "attribute_id", "nature_id", "bank_type_id", "bank_code", "bank_name", "prov", "city",
                "bif_code", "electric_bill", "rates_float", "interest_cycle", "reg_date", "is_online",
                "valid_sign", "status", "is_capital_pool", "is_domestic_bank", "is_limit_quota",
                "is_online_handle", "is_offshore_account", "is_rpa_escrow", "basic_account_sign",
                "create_date", "create_by", "update_date", "update_by"));
        REQUIRED.put("BT_BANK_ACC_CUR", cols("id", "bank_acc_id", "cur_id"));
        REQUIRED.put("BT_ACC_NATURE", cols("id", "nature_code", "nature_name",
                "valid_sign", "create_date", "create_by"));
        REQUIRED.put("BT_CURRENCY", cols("id", "english_code"));
        REQUIRED.put("SYS_EXTERNAL_CORP", cols("id", "md_id", "code", "name", "name_en", "abbreviate",
                "soc_code", "external_type", "status", "audit_status", "is_native", "source_system",
                "supplier_id", "bw_type"));
        REQUIRED.put("SYS_EXTERNAL_CORP_BANKACC", cols("id", "external_corp_id", "external_acc",
                "external_acc_name", "bank", "bank_type", "bank_sourcecode", "cur_id"));
        // SYS_REGION 只用于按联行号城市码反查省/市，缺了只是不填省市区，不阻断
        REQUIRED.put("SYS_REGION", cols("id", "bank_input_city", "parent_id"));
    }

    /**
     * 标准查询接口要读的视图。它们由「T2 标准接口服务脚本」创建，属于另一套基线脚本，
     * 本仓库里没有 DDL，t6 上到底建没建只能运行时确认，所以一并纳入自检。
     */
    private static final List<String> VIEWS = List.of(
            "standard_cur_view", "standard_corp_view", "STANDARD_USER_VIEW",
            "STANDARD_BANKACC_VIEW", "STANDARD_BANKINPUT_VIEW",
            "standard_externalcorp_view", "standard_externalcorp_acc_view", "standard_item_view");

    private final AppProps appProps;
    private final DataSourceRegistry registry;

    public MdmSchemaChecker(AppProps appProps, DataSourceRegistry registry) {
        this.appProps = appProps;
        this.registry = registry;
    }

    /**
     * 自检结果。
     *
     * @param skipped  非 MySQL 库时为 true：信息字典查询方式不同，跳过自动核对
     * @param problems 缺失项描述；为空表示就绪
     */
    public record CheckResult(boolean skipped, List<String> problems) {
    }

    /**
     * 执行自检。
     */
    public CheckResult check() {
        String key = appProps.getMdm().getDatasource();
        if (key == null || key.isBlank()) {
            key = DataSourceRegistry.MAIN;
        }
        if (!registry.exists(key)) {
            return new CheckResult(false, List.of("数据源不存在：" + key));
        }
        JdbcTemplate t = new JdbcTemplate(registry.template(key).getDataSource());
        if (!isMySql(t)) {
            return new CheckResult(true, List.of());
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : REQUIRED.entrySet()) {
            String table = entry.getKey();
            if (!tableExists(t, table)) {
                problems.add("表不存在：" + table);
                continue;
            }
            // 表存在时才检查列，否则错误信息会淹没在一个不存在的表里
            List<String> missing = missingColumns(t, table, entry.getValue());
            if (!missing.isEmpty()) {
                problems.add(table + " 缺少列：" + String.join(", ", missing));
            }
        }
        for (String view : VIEWS) {
            if (!tableExists(t, view)) {
                problems.add("视图不存在（标准查询接口不可用）：" + view);
            }
        }
        if (!problems.isEmpty()) {
            return new CheckResult(false, problems);
        }
        // 表列都齐了才值得进一步校验主数据取值
        checkSupplierId(t, problems);
        return new CheckResult(false, problems);
    }

    /**
     * 客商行要写 supplier_id（来源角色），配错或对应字典行缺失会让客商带上孤儿来源标识。
     * 这里确认 app.mdm.supplier-id 指向的 SYS_SUPPLIER_TYPE 记录真的存在。
     */
    private void checkSupplierId(JdbcTemplate t, List<String> problems) {
        String supplierId = appProps.getMdm().getSupplierId();
        if (supplierId == null || supplierId.isBlank()) {
            return;
        }
        if (!tableExists(t, "SYS_SUPPLIER_TYPE")) {
            problems.add("表不存在：SYS_SUPPLIER_TYPE");
            return;
        }
        Integer n = t.queryForObject(
                "select count(*) from SYS_SUPPLIER_TYPE where id = ?", Integer.class, supplierId);
        if (n == null || n == 0) {
            problems.add("app.mdm.supplier-id 配置的来源角色在 SYS_SUPPLIER_TYPE 中不存在：" + supplierId);
        }
    }

    private boolean tableExists(JdbcTemplate t, String table) {
        Integer n = t.queryForObject(
                "select count(*) from information_schema.tables"
                        + " where table_schema = database() and upper(table_name) = upper(?)",
                Integer.class, table);
        return n != null && n > 0;
    }

    private List<String> missingColumns(JdbcTemplate t, String table, String[] expected) {
        List<String> missing = new ArrayList<>();
        for (String col : expected) {
            Integer n = t.queryForObject(
                    "select count(*) from information_schema.columns"
                            + " where table_schema = database() and upper(table_name) = upper(?)"
                            + " and upper(column_name) = upper(?)",
                    Integer.class, table, col);
            if (n == null || n == 0) {
                missing.add(col);
            }
        }
        return missing;
    }

    private boolean isMySql(JdbcTemplate t) {
        try {
            String version = t.queryForObject("select version()", String.class);
            return version != null && !version.toLowerCase().contains("oracle");
        } catch (Exception e) {
            return false;
        }
    }

    private static String[] cols(String... names) {
        return names;
    }
}
