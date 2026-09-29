package com.interchange.platform.dyg.service;

import com.interchange.platform.dyg.constant.MdmConstant;
import com.interchange.platform.standard.exception.BusinessException;
import com.interchange.platform.standard.utils.JsonUtil;
import com.interchange.platform.standard.utils.StringUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MDM 接收服务的公共骨架。
 *
 * <p>MDM 下发的报文一律是 JSON 数组，处理方式也一致：
 * <b>逐条校验、每条记录一个独立事务、单条失败不影响其余、最后统一回执</b>。
 * 这个流程在 5 个接收服务里完全相同，统一收在这里，子类只需提供
 * 「怎么校验」「怎么落库」「回执里的编码与描述从哪取」。
 *
 * <p>为什么用编程式事务（{@link TransactionTemplate}）而不是 {@code @Transactional}：
 * 标在 {@code handle()} 上是"整批一个事务"，一条失败全批回滚；
 * 业务要求是"单条失败只回滚该条，其余照常入库"，只能逐条开事务。
 *
 * @param <T> 报文里的单条记录类型
 */
public abstract class MdmReceiveSupport<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** Spring Boot 自动装配的编程式事务模板 */
    @Resource
    protected TransactionTemplate tx;

    /* ===================== 子类需要提供的 ===================== */

    /** 单条记录的校验，返回错误说明；无错误返回空串 */
    protected abstract String validate(T bean);

    /** 单条记录的落库，在独立事务内执行 */
    protected abstract void save(T bean);

    /** 回执里的 mdId */
    protected abstract String mdIdOf(T bean);

    /** 回执里的 mdCode */
    protected abstract String mdCodeOf(T bean);

    /** 回执里的 mdDescription */
    protected abstract String mdDescriptionOf(T bean);

    /* ===================== 公共流程 ===================== */

    /**
     * 逐条处理整批报文并生成回执。
     *
     * @param json         原始报文（JSON 数组）
     * @param recordType   单条记录的类型
     * @param emptyMessage 报文为空时的提示
     * @param parseError   报文解析失败时的提示前缀
     * @return 统一回执结构
     */
    protected Map<String, Object> receive(String json, Class<T> recordType,
                                          String emptyMessage, String parseError) {
        List<T> list = parse(json, recordType, emptyMessage, parseError);
        // 整批建缓存，避免逐条回查数据库
        prepare(list);
        List<Map<String, Object>> items = new ArrayList<>(list.size());
        for (T bean : list) {
            items.add(handleOne(bean));
        }
        return response(items);
    }

    /**
     * 整批预处理钩子：需要按整批 mdId 批量预加载关联数据（币种、银行类别等）时覆写。
     * 默认什么都不做。
     */
    protected void prepare(List<T> list) {
        // 默认无预处理
    }

    /** 单条：先校验，再在独立事务里落库，最后生成回执条目 */
    private Map<String, Object> handleOne(T bean) {
        String mdId = mdIdOf(bean);
        String err = validate(bean);
        if (!err.isEmpty()) {
            return item(bean, false, err);
        }
        try {
            tx.executeWithoutResult(status -> save(bean));
            return item(bean, true, MdmConstant.MSG_OK);
        } catch (Exception e) {
            log.error("MDM 落库失败: type={}, mdId={}", bean.getClass().getSimpleName(), mdId, e);
            return item(bean, false, MdmConstant.MSG_SAVE_FAILED + StringUtil.reason(e));
        }
    }

    /** 报文解析：一律是 JSON 数组 */
    private List<T> parse(String json, Class<T> recordType, String emptyMessage, String parseError) {
        if (StringUtil.isBlank(json)) {
            throw new BusinessException(400, emptyMessage);
        }
        try {
            return JsonUtil.jsonToObjArray(recordType, json);
        } catch (Exception e) {
            throw new BusinessException(400, parseError + e.getMessage());
        }
    }

    /* ===================== 工具 ===================== */

    /** 按取值函数把整批记录的某个字段抽成去重后的 id 列表，供批量预加载使用 */
    protected static <E> List<String> collect(List<E> list, java.util.function.Function<E, String> getter) {
        List<String> ids = new ArrayList<>();
        for (E bean : list) {
            if (bean == null) {
                continue;
            }
            String v = getter.apply(bean);
            if (StringUtil.isNotBlank(v)) {
                ids.add(v);
            }
        }
        return ids.stream().distinct().toList();
    }

    /* ===================== 回执 ===================== */

    /**
     * 统一回执：{@code {status, message, responseData:[{mdId, mdCode, mdDescription, status, message}]}}。
     * 子类如需自定义标题或成功文案，可覆写本方法。
     */
    protected Map<String, Object> response(List<Map<String, Object>> items) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", MdmConstant.S);
        res.put("message", MdmConstant.MSG_RECEIVE_OK);
        res.put("responseData", items);
        return res;
    }

    protected Map<String, Object> item(T bean, boolean ok, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mdId", mdIdOf(bean));
        m.put("mdCode", mdCodeOf(bean));
        m.put("mdDescription", mdDescriptionOf(bean));
        m.put("status", ok ? MdmConstant.S : MdmConstant.E);
        m.put("message", message);
        return m;
    }

    /** 由业务自定的 mdId 生成一份校验失败的回执（用于报文级前置校验） */
    protected Map<String, Object> failItem(String mdId, String mdCode, String mdDescription, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mdId", mdId);
        m.put("mdCode", mdCode);
        m.put("mdDescription", mdDescription);
        m.put("status", MdmConstant.E);
        m.put("message", message);
        return m;
    }
}
