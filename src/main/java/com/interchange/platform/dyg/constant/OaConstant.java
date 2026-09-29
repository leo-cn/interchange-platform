package com.interchange.platform.dyg.constant;

/**
 * OA 对接常量（对应 dyg-erp 的 {@code com.byttersoft.hibernate.erp.dyg.constants.OaConstant}）。
 *
 * <p>值与 ERP 一致，别按常识改：回执成功码是 {@code 0}、失败码是 {@code 201}。
 */
public final class OaConstant {

    // ---- 报文标识 ----

    /** 报文顶层来源标识（ERP 的 OA_SOURCE_BYTTER） */
    public static final String OA_SOURCE = "BYTTER";

    // ---- 目标系统 / 流程模板 ----

    /** 目标 OA 系统：本单只发集团（ERP 的 OA_SYSTEM_JTOA） */
    public static final String OA_SYSTEM_JT = "JTOA";

    /** 流程模板编码：债券业务审批单（ERP 里从字典 oAFlowTemplateCode 取 cmsBond + Group） */
    public static final String TEMPLATE_CMS_BOND = "cmsBondGroup";

    // ---- 单据内容 ----

    /** 债券业务审批单标题前缀（ERP 的 push2OA 里拼的「债券业务审批单 + 单号」） */
    public static final String TITLE_PREFIX = "债券业务审批单";

    // ---- 回执 ----

    /** OA 回执成功码（ERP 的 OA_RESULT_SUCCESS） */
    public static final String RESULT_SUCCESS = "0";

    // ---- 任务上下文传值键 ----

    /** 跨环节传值的键：本条的债券单号，回执处理时要用 */
    public static final String ATTR_BILL_CODE = "billCode";

    private OaConstant() {
    }
}
