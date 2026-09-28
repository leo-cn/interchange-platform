package com.interchange.platform.mdm.constant;

/**
 * 主数据（MDM）接收接口常量。
 *
 * <p>字段语义对齐 dyg-erp 的 {@code MdmConstant}：
 * <ul>
 *   <li>{@code status}：S 成功 / E 失败（外层与明细同含义）；</li>
 *   <li>{@code mdStatusCode}：ACTIVE 启用 / INVALID 停用；</li>
 *   <li>{@code responseData[]}：逐条明细 mdId / mdCode / mdDescription / status / message。</li>
 * </ul>
 *
 * <p><b>外层 status 恒为 S</b>（只要整批没抛异常），这是与 dyg-erp 保持一致的行为：
 * 单条失败只体现在 {@code responseData[].status = E} 上。调用方必须逐条看明细，
 * 只看外层会把脏数据当成成功。
 */
public final class MdmConstant {

    /** 接收状态：成功 */
    public static final String S = "S";
    /** 接收状态：失败 */
    public static final String E = "E";

    /** 主数据状态：启用 */
    public static final String ACTIVE = "ACTIVE";

    /** 银行网点分类：境内银行（其他值原系统一律不接收） */
    public static final String CATEGORY_INSIDE = "INSIDE";

    /** 布尔字段取值：是 */
    public static final String Y = "1";
    /** 布尔字段取值：否 */
    public static final String N = "0";

    /** 账户/单据审核通过状态 */
    public static final int STATUS_APPROVED = 95;

    // ---- 客商类型 ----
    public static final String BPTYPE_SUPPLIER = "SUP";
    public static final String BPTYPE_CUSTOMER = "CUS";
    public static final String BPTYPE_BOTH = "BP";

    // ---- 接口编码，对应 receive_api.apiCode 与 URL /api/receive/{apiCode} ----
    public static final String API_BANK_TYPE = "mdm-bank-type-receive";
    public static final String API_BANK_BRANCH = "mdm-bank-branch-receive";
    public static final String API_ORG = "mdm-org-receive";
    public static final String API_BANK_ACC = "mdm-bank-acc-receive";
    public static final String API_PARTNER = "mdm-partner-receive";

    /**
     * 推送侧任务编码前缀。
     *
     * <p>推送顺序必须是 银行类别 → 网点 → 组织 → 银行账户 → 客商：
     * 后者依赖前者的 mdId 解析外键，顺序乱了整批都会校验失败。
     */
    public static final String TASK_PREFIX = "MDM-";

    private MdmConstant() {
    }
}
