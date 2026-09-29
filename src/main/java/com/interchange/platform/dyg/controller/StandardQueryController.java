package com.interchange.platform.dyg.controller;

import com.interchange.platform.dyg.service.StandardQueryService;
import com.interchange.platform.standard.core.base.BaseController;
import com.interchange.platform.standard.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 标准接口服务（原系统那 9 个查询接口）。
 *
 * <p>每个方法直接调业务方法：成功用 {@link #success} 包成 {@code status=2 + date}，
 * 入参不合法（业务方法抛 {@link BusinessException}）用 {@link #fail} 包成 {@code status=1 + message}。
 */
@RestController
@RequestMapping("/api/std")
public class StandardQueryController extends BaseController {

    @Resource
    private StandardQueryService service;

    /** 币种查询：{@code {"curCode":"CNY"}} */
    @PostMapping(value = "/cur", produces = "application/json;charset=UTF-8")
    public Map<String, Object> cur(@RequestBody(required = false) String body) {
        try {
            return success(service.cur(body));
        } catch (BusinessException e) {
            return fail(e.getMessage());
        }
    }

    /** 单位查询：{@code {"code":"001"}} */
    @PostMapping(value = "/corp", produces = "application/json;charset=UTF-8")
    public Map<String, Object> corp(@RequestBody(required = false) String body) {
        try {
            return success(service.corp(body));
        } catch (BusinessException e) {
            return fail(e.getMessage());
        }
    }

    /** 用户查询：{@code {"corpCode":"001"}} */
    @PostMapping(value = "/user", produces = "application/json;charset=UTF-8")
    public Map<String, Object> user(@RequestBody(required = false) String body) {
        try {
            return success(service.user(body));
        } catch (BusinessException e) {
            return fail(e.getMessage());
        }
    }

    /** 省市查询：四个条件至少填一个，{@code {"cityCode":"1001"}} */
    @PostMapping(value = "/region", produces = "application/json;charset=UTF-8")
    public Map<String, Object> region(@RequestBody(required = false) String body) {
        try {
            return success(service.region(body));
        } catch (BusinessException e) {
            return fail(e.getMessage());
        }
    }

    /** 银行账号查询：{@code {"bankAcc":"6214..."}} */
    @PostMapping(value = "/bankAcc", produces = "application/json;charset=UTF-8")
    public Map<String, Object> bankAcc(@RequestBody(required = false) String body) {
        try {
            return success(service.bankAcc(body));
        } catch (BusinessException e) {
            return fail(e.getMessage());
        }
    }

    /** 联行号网点查询：{@code {"bankCode":"102100099996"}} */
    @PostMapping(value = "/bankInput", produces = "application/json;charset=UTF-8")
    public Map<String, Object> bankInput(@RequestBody(required = false) String body) {
        try {
            return success(service.bankInput(body));
        } catch (BusinessException e) {
            return fail(e.getMessage());
        }
    }

    /** 客商查询：{@code {"code":"CUST001"}} */
    @PostMapping(value = "/externalCorp", produces = "application/json;charset=UTF-8")
    public Map<String, Object> externalCorp(@RequestBody(required = false) String body) {
        try {
            return success(service.externalCorp(body));
        } catch (BusinessException e) {
            return fail(e.getMessage());
        }
    }

    /** 客商银行账号查询：{@code {"externalAcc":"6214..."}} */
    @PostMapping(value = "/externalCorpAcc", produces = "application/json;charset=UTF-8")
    public Map<String, Object> externalCorpAcc(@RequestBody(required = false) String body) {
        try {
            return success(service.externalCorpAcc(body));
        } catch (BusinessException e) {
            return fail(e.getMessage());
        }
    }

    /** 科目查询：{@code {"itemCode":"1001"}} */
    @PostMapping(value = "/item", produces = "application/json;charset=UTF-8")
    public Map<String, Object> item(@RequestBody(required = false) String body) {
        try {
            return success(service.item(body));
        } catch (BusinessException e) {
            return fail(e.getMessage());
        }
    }
}
