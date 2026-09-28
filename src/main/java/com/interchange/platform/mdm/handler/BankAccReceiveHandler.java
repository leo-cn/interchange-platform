package com.interchange.platform.mdm.handler;

import com.interchange.platform.mdm.constant.MdmConstant;
import com.interchange.platform.mdm.MdmReceiveService;
import com.interchange.platform.service.handler.ReceiveHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 银行账户接收： URL {@code POST /api/receive/mdm-bank-acc-receive}。
 *
 * <p>依赖已接收的银行类别 / 网点 / 组织 / 币种，四者缺一则该条判 FAIL，
 * 因为要把 mdId 解析成各表的 UUID 主键才能落 BT_BANK_ACC。
 */
@Component
public class BankAccReceiveHandler implements ReceiveHandler {

    private final MdmReceiveService service;

    public BankAccReceiveHandler(MdmReceiveService service) {
        this.service = service;
    }

    @Override
    public String apiCode() {
        return MdmConstant.API_BANK_ACC;
    }

    @Override
    public String description() {
        return "MDM 银行账户接收：写入 BT_BANK_ACC + 币种子表，账户性质缺失自动补建";
    }

    @Override
    public boolean rawBody() {
        return true;
    }

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        return service.receiveBankAcc(body);
    }
}
