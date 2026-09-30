package com.interchange.platform.standard.sys.service.receiveService;

import com.interchange.platform.standard.anotation.ReceiveApi;
import com.interchange.platform.standard.exception.BizException;
import com.interchange.platform.standard.exception.BusinessException;

import com.interchange.platform.standard.sys.service.BizUserService;
import com.interchange.platform.standard.sys.vo.ResultDTO;

import com.interchange.platform.standard.sys.service.auth.ServerTokenService;
import com.interchange.platform.standard.sys.service.log.InterfaceLogService;
import com.interchange.platform.standard.sys.service.log.LogStore;
import com.interchange.platform.standard.utils.TraceId;
import com.interchange.platform.standard.utils.Utils;
import com.interchange.platform.standard.config.AppProps;
import com.interchange.platform.standard.sys.entity.ReceiveLog;
import com.interchange.platform.standard.sys.entity.SysUserExt;
import com.interchange.platform.standard.sys.dao.SysUserExtDao;
import com.interchange.platform.standard.utils.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接收侧调度服务：处理第三方系统调用本平台的接口（鉴权 + 路由到具体业务处理器 + 落日志）。
 * 本类是分发它们的引擎，两者不是接口与实现的关系。
 */
@Service
public class ReceiveDispatchService {

    private static final Logger log = LoggerFactory.getLogger(ReceiveDispatchService.class);

    @Resource
    private AppProps appProps;
    @Resource
    private SysUserExtDao userExtDao;
    @Resource
    private BizUserService directory;
    @Resource
    private ServerTokenService serverTokenService;
    @Resource
    private ReceiveApiService receiveApiService;
    @Resource
    private LogStore logStore;
    @Resource
    private InterfaceLogService interfaceLogService;
    /** 接收侧业务处理器表：apiCode(小写) → 处理器，启动时从 Spring 容器收集 */
    private final Map<String, ReceiveService> receiveHandlers = new HashMap<>();
    private final ObjectMapper objectMapper = JsonUtil.getMapper();

    /** 容器里的全部接收处理器，启动后据此建路由表 */
    @Resource
    private List<ReceiveService> handlers;

    /** 收集所有 ReceiveService 实现，按注解声明的 code 建路由表；重复注册保留先到的并告警 */
    @PostConstruct
    void initHandlers() {
        for (ReceiveService h : handlers) {
            // 处理器上有 @Transactional，容器注入的是 CGLIB 代理；注解不在代理子类上，
            // 必须回到业务类读，否则带事务的处理器会被当成"没有 @ReceiveApi"整个跳过
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(h);
            ReceiveApi info = AnnotationUtils.findAnnotation(targetClass, ReceiveApi.class);
            if (info == null) {
                log.warn("接收处理器 {} 缺少 @ReceiveApi 注解，已跳过", targetClass.getSimpleName());
                continue;
            }
            ReceiveService prev = receiveHandlers.putIfAbsent(info.code().toLowerCase(), h);
            if (prev != null) {
                log.warn("接收处理器 apiCode={} 重复注册：{} 与 {}，保留 {}",
                        info.code(), targetClass.getSimpleName(),
                        AopProxyUtils.ultimateTargetClass(prev).getSimpleName(),
                        AopProxyUtils.ultimateTargetClass(prev).getSimpleName());
                continue;
            }
            log.info("接收处理器已注册: apiCode={}, handler={}",
                    info.code(), targetClass.getSimpleName());
            // 首次启动登记进 receive_api；已存在则跳过，不覆盖用户在页面上的改名、停用
            receiveApiService.registerIfAbsent(info.code(), info.desc(), null, h.authRequired());
        }
    }

    /**
     * 处理一次接收请求。
     *
     * @param apiCode  接口编码（URL 上的 {apiCode}）
     * @param method   HTTP 方法
     * @param headers  请求头（JSON 字符串，落库用）
     * @param token 从请求头提取的令牌
     * @param body     报文体
     * @param remoteIp 来源 IP
     * @return 统一响应 {@link ResultDTO}
     */
    public Object handle(String apiCode, String method, String headers,
                         String token, String body, String remoteIp) {
        long start = System.currentTimeMillis();
        String traceId = TraceId.generate();

        ReceiveLog entity = new ReceiveLog();
        entity.setApiCode(apiCode);
        entity.setTraceId(traceId);
        entity.setHttpMethod(method);
        entity.setRemoteIp(remoteIp);
        entity.setHeaders(Utils.truncate(headers, 4000));
        entity.setRequestBody(Utils.truncate(body, 200000));

        try {
            // 每次接收前先空一行：与上一次调用的记录隔开
            interfaceLogService.blankLine(apiCode);
            // 过程日志：收到请求（边收边记，第三方调试时能立刻看到）
            String length = body == null ? "0" : String.valueOf(body.length());
            log.info("接口[{}] 收到第三方请求：{} from {}，报文长度 {}", apiCode, method, remoteIp, length);
            Map<String, Object> recvExtra = new LinkedHashMap<>();
            recvExtra.put("method", method);
            recvExtra.put("remoteIp", remoteIp);
            recvExtra.put("bodyLength", body == null ? 0 : body.length());
            recvExtra.put("request", interfaceLogService.payload(body));
            interfaceLogService.write("RECEIVE", apiCode, null, traceId, "RECEIVED",
                    "收到第三方请求 " + method + "（来自 " + remoteIp + "，报文长度 " + length + "）", recvExtra);

            // 0) 接口必须先在「接收接口」页登记过。
            // 以前没这道卡口：任何 apiCode 都能进来，没有对应业务分支时被 handleBusiness 的
            // 兜底逻辑原样回执成"接收成功"，联调时会误以为接口已经打通。现在直接拒绝。
            ReceiveApiService.Def apiDef = receiveApiService.findEnabled(apiCode)
                    .orElseThrow(() -> new BizException(401,
                            "接口未定义或已停用：" + apiCode + "（请先在「接收接口」页登记）"));

            // 1) 鉴权（免鉴权接口跳过，调用方记成 NO-AUTH）
            String caller = apiDef.isAuthRequired() ? authenticate(apiCode, token) : "NO-AUTH";
            entity.setCaller(caller);

            // 2) 业务处理
            Map<String, Object> data = handleBusiness(apiCode, body, traceId, parseHeaders(headers));

            // 统一包一层响应结构
            ResultDTO<Map<String, Object>> response = ResultDTO.ok("接收成功", data);
            response.setTraceId(traceId);
            entity.setStatus("SUCCESS");
            entity.setResponseBody(Utils.truncate(JsonUtil.ObjToJson(response), 200000));
            interfaceLogService.write("RECEIVE", apiCode, null, traceId, "RESPONDED",
                    "处理完成并已响应（耗时 " + (System.currentTimeMillis() - start) + "ms）", null);
            return response;

        } catch (BizException e) {
            ResultDTO<Map<String, Object>> response = ResultDTO.fail(e.getCode(), e.getMessage());
            response.setTraceId(traceId);
            entity.setStatus("FAIL");
            entity.setErrorMsg(e.getMessage());
            entity.setResponseBody(JsonUtil.ObjToJson(response));
            log.warn("接收请求失败 apiCode={}, caller={}, 原因={}", apiCode, entity.getCaller(), e.getMessage());
            return response;
        } catch (Exception e) {
            ResultDTO<Map<String, Object>> response = ResultDTO.fail(500, "处理异常: " + e.getMessage());
            response.setTraceId(traceId);
            entity.setStatus("FAIL");
            entity.setErrorMsg(e.getMessage());
            entity.setResponseBody(JsonUtil.ObjToJson(response));
            log.error("接收请求异常 apiCode={}", apiCode, e);
            return response;
        } finally {
            long cost = System.currentTimeMillis() - start;
            entity.setCostMs(cost);
            entity.setReceiveTime(LocalDateTime.now());
            // 完整记录落库（task_log / receive_log），页面可查可导出 CSV；
            // 接口日志文件里只留上面的过程日志，不再额外落一行 JSON 汇总。
            try {
                // 走统一落库入口：默认每个接口只留最后一次调用
                logStore.saveReceiveLog(entity);
            } catch (Exception e) {
                log.error("接收日志落库失败: {}", e.getMessage());
            }
            // 过程日志：结束（汇总）。成功失败都打，保证每次调用都有终态。
            // 成败写进主文案，不再追加「执行结果」「结束报文」——重复信息只会把日志撑厚。
            Map<String, Object> endExtra = new LinkedHashMap<>();
            endExtra.put("costMs", cost);
            interfaceLogService.write("RECEIVE", apiCode, null, traceId, "END",
                    "本次接收处理结束：" + statusText(entity.getStatus()) + "（耗时 " + cost + "ms）", endExtra);
        }
    }

    /** 状态英文 → 人话 */
    private static String statusText(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case "SUCCESS" -> "成功";
            case "FAIL" -> "失败";
            default -> status;
        };
    }

    /**
     * 令牌鉴权，按这个顺序匹配：
     *
     * @param apiCode 本次调用的接口编码，用于校验接口令牌的可调用范围
     * @return 调用方标识
     */
    private String authenticate(String apiCode, String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(401, "缺少访问令牌（请在请求头 X-Token 中携带）");
        }
        String t = token.trim();
        if (t.startsWith("Bearer ")) {
            t = t.substring(7).trim();
        }
        String global = appProps.getReceiveToken();
        if (global != null && !global.isBlank() && global.equals(t)) {
            return "GLOBAL-TOKEN";
        }
        // 服务方签发的访问令牌：查内存缓存（不查库），命中且未过期才放行
        ServerTokenService.Ticket ticket = serverTokenService.verify(t);
        if (ticket != null) {
            if (!ticket.allows(apiCode)) {
                throw new BizException(401, "访问令牌无效（该令牌无权调用接口 " + apiCode + "）");
            }
            return "TOKEN:" + ticket.getName();
        }
        // 令牌挂在平台扩展表上，账号本身在业务系统：两边都得是启用状态才算有效
        SysUserExt ext = userExtDao.findByApiToken(t).orElse(null);
        if (ext == null || ext.getStatus() == null || ext.getStatus() != 1) {
            throw new BizException(401, "访问令牌无效");
        }
        BizUserService.BizUser biz = directory.findById(ext.getExtId())
                .orElseThrow(() -> new BizException(401, "访问令牌无效"));
        if (!directory.isActive(biz)) {
            throw new BizException(401, "访问令牌无效（该账号在业务系统中已停用）");
        }
        return biz.loginName();
    }

    /**
     */
    private Map<String, Object> handleBusiness(String apiCode, String body, String traceId,
                                               Map<String, String> headerMap) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apiCode", apiCode);
        data.put("received", true);

        ReceiveService handler = receiveHandlers.get(apiCode.toLowerCase());
        if (handler != null) {
            try {
                Map<String, Object> result = handler.handle(body, traceId, headerMap);
                if (result != null) {
                    data.putAll(result);
                }
                return data;
            } catch (BusinessException e) {
                // 业务失败原样透传状态码（400 参数错误等），响应文案与日志由外层统一处理
                throw new BizException(e.getHttpStatus(), e.getMessage());
            }
        }

        // 兜底：已登记但尚未挂业务处理器的接口
        int length = body == null ? 0 : body.length();
        data.put("bodyLength", length);
        data.put("ack", "已接收，报文长度 " + length);

        // 报文为 JSON 对象时，回显其中的业务单号，便于对方对账
        if (body != null && body.trim().startsWith("{")) {
            try {
                Map<?, ?> map = objectMapper.readValue(body, Map.class);
                Object bizNo = map.get("bizNo");
                if (bizNo == null) {
                    bizNo = map.get("orderNo");
                }
                if (bizNo != null) {
                    data.put("bizNo", bizNo);
                }
            } catch (Exception ignored) {
                // 报文不是 JSON 时忽略
            }
        }
        return data;
    }

    /** 把落库用的请求头 JSON 还原成 Map，交给处理器只读使用；解析失败返回空 Map */
    private Map<String, String> parseHeaders(String headersJson) {
        Map<String, String> map = new LinkedHashMap<>();
        if (headersJson == null || headersJson.isBlank()) {
            return map;
        }
        try {
            Map<?, ?> raw = objectMapper.readValue(headersJson, Map.class);
            raw.forEach((k, v) -> {
                if (k != null && v != null) {
                    map.put(String.valueOf(k), String.valueOf(v));
                }
            });
        } catch (Exception ignored) {
            // headers JSON 是引擎自己生成的，理论上不会解析失败；失败也不阻断业务
        }
        return map;
    }
}
