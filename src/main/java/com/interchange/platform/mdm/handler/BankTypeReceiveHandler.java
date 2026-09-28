package com.interchange.platform.mdm.handler;

import com.interchange.platform.mdm.constant.MdmConstant;
import com.interchange.platform.mdm.MdmReceiveService;
import com.interchange.platform.service.handler.ReceiveHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 银行类别接收： URL {@code POST /api/receive/mdm-bank-type-receive}。
 *
 * <p>报文为 JSON 数组，元素字段见 {@code MdmDto.BankType}。
 * 回执用 rawBody 直接返回 MDM 约定的 {@code {status,message,responseData}} 结构。
 *
 * <p><b>接收顺序</b>：银行类别必须最先接收，网点会按 mdId 关联它取人行前缀。
 */
@Component
public class BankTypeReceiveHandler implements ReceiveHandler {

    private final MdmReceiveService service;

    public BankTypeReceiveHandler(MdmReceiveService service) {
        this.service = service;
    }

    @Override
    public String apiCode() {
        return MdmConstant.API_BANK_TYPE;
    }

    @Override
    public String description() {
        return "MDM 银行类别接收：写入 BT_BANK_TYPE，新增时级联 BIS_BIF_INIT";
    }

    /** 按 MDM 既定格式回执，不再包一层 R */
    @Override
    public boolean rawBody() {
        return true;
    }

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        return service.receiveBankType(body);
    }
}
