package com.interchange.platform.standard.controller;

import com.interchange.platform.standard.service.StandardQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 标准接口服务脚本里的 9 个「查询类」接口（第 1 批）。
 *
 * <p>路径与 dyg-erp 的 Standard*Web 一一对应（见下表）。入参是<b>整段 JSON 报文</b>，
 * 出参固定 {@code {status, message, date}}，{@code status=2} 成功、{@code 1} 失败。
 *
 * <pre>
 *   POST /api/std/cur               币种        （StandardCurWeb）
 *   POST /api/std/corp              单位        （StandardCorpWeb）
 *   POST /api/std/user              用户        （StandardUserWeb）
 *   POST /api/std/region            省市        （StandardRegionWeb，读 SYS_REGION 表）
 *   POST /api/std/bankAcc           银行账号    （StandardBankAccWeb）
 *   POST /api/std/bankInput         联行号网点  （StandardBankInputWeb）
 *   POST /api/std/externalCorp      客商        （StandardExternalCorpWeb）
 *   POST /api/std/externalCorpAcc   客商账号    （StandardExternalCorpAccWeb）
 *   POST /api/std/item              科目        （StandardItemWeb）
 * </pre>
 *
 * <p>只在 {@code app.mdm.enabled=true} 时装配：这几个接口直接查业务视图，
 * 不开开关就不暴露。
 */
@RestController
@RequestMapping("/api/std")
@ConditionalOnProperty(prefix = "app.mdm", name = "enabled", havingValue = "true")
public class StandardQueryController {

    private final StandardQueryService service;

    public StandardQueryController(StandardQueryService service) {
        this.service = service;
    }

    /** 币种查询：{@code {"curCode":"CNY"}} */
    @PostMapping(value = "/cur", produces = "application/json;charset=UTF-8")
    public Map<String, Object> cur(@RequestBody(required = false) String body) {
        return service.cur(body);
    }

    /** 单位查询：{@code {"code":"001"}} */
    @PostMapping(value = "/corp", produces = "application/json;charset=UTF-8")
    public Map<String, Object> corp(@RequestBody(required = false) String body) {
        return service.corp(body);
    }

    /** 用户查询：{@code {"corpCode":"001"}} */
    @PostMapping(value = "/user", produces = "application/json;charset=UTF-8")
    public Map<String, Object> user(@RequestBody(required = false) String body) {
        return service.user(body);
    }

    /** 省市查询：四个条件至少填一个，{@code {"cityCode":"1001"}} */
    @PostMapping(value = "/region", produces = "application/json;charset=UTF-8")
    public Map<String, Object> region(@RequestBody(required = false) String body) {
        return service.region(body);
    }

    /** 银行账号查询：{@code {"bankAcc":"6214..."}} */
    @PostMapping(value = "/bankAcc", produces = "application/json;charset=UTF-8")
    public Map<String, Object> bankAcc(@RequestBody(required = false) String body) {
        return service.bankAcc(body);
    }

    /** 联行号网点查询：{@code {"bankCode":"102100099996"}} */
    @PostMapping(value = "/bankInput", produces = "application/json;charset=UTF-8")
    public Map<String, Object> bankInput(@RequestBody(required = false) String body) {
        return service.bankInput(body);
    }

    /** 客商查询：{@code {"code":"CUST001"}} */
    @PostMapping(value = "/externalCorp", produces = "application/json;charset=UTF-8")
    public Map<String, Object> externalCorp(@RequestBody(required = false) String body) {
        return service.externalCorp(body);
    }

    /** 客商银行账号查询：{@code {"externalAcc":"6214..."}} */
    @PostMapping(value = "/externalCorpAcc", produces = "application/json;charset=UTF-8")
    public Map<String, Object> externalCorpAcc(@RequestBody(required = false) String body) {
        return service.externalCorpAcc(body);
    }

    /** 科目查询：{@code {"itemCode":"1001"}} */
    @PostMapping(value = "/item", produces = "application/json;charset=UTF-8")
    public Map<String, Object> item(@RequestBody(required = false) String body) {
        return service.item(body);
    }
}
