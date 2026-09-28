package com.interchange.platform.mdm.handler;

import com.interchange.platform.mdm.constant.MdmConstant;
import com.interchange.platform.mdm.MdmReceiveService;
import com.interchange.platform.service.handler.ReceiveHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 客商接收： URL {@code POST /api/receive/mdm-partner-receive}。
 *
 * <p>写入 SYS_EXTERNAL_CORP，账号写入 SYS_EXTERNAL_CORP_BANKACC（<b>注意不是 *_BANK_ACC</b>）。
 * 客商类型做字典转换 SUP→1 / CUS→0 / BP→2；账号币种只取第一条，与原逻辑一致。
 */
@Component
public class PartnerReceiveHandler implements ReceiveHandler {

    private final MdmReceiveService service;

    public PartnerReceiveHandler(MdmReceiveService service) {
        this.service = service;
    }

    @Override
    public String apiCode() {
        return MdmConstant.API_PARTNER;
    }

    @Override
    public String description() {
        return "MDM 客商接收：写入 SYS_EXTERNAL_CORP + 客商账号子表";
    }

    @Override
    public boolean rawBody() {
        return true;
    }

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        return service.receivePartner(body);
    }
}
