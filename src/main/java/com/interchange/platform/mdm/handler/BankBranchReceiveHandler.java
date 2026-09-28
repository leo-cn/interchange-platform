package com.interchange.platform.mdm.handler;

import com.interchange.platform.mdm.constant.MdmConstant;
import com.interchange.platform.mdm.MdmReceiveService;
import com.interchange.platform.service.handler.ReceiveHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 银行网点接收： URL {@code POST /api/receive/mdm-bank-branch-receive}。
 *
 * <p>依赖已接收的银行类别（按 banktype 取 mdId 关联），且默认只接收境内银行
 * （categoryCode=INSIDE，可用 {@code app.mdm.inside-branch-only=false} 放开）。
 */
@Component
public class BankBranchReceiveHandler implements ReceiveHandler {

    private final MdmReceiveService service;

    public BankBranchReceiveHandler(MdmReceiveService service) {
        this.service = service;
    }

    @Override
    public String apiCode() {
        return MdmConstant.API_BANK_BRANCH;
    }

    @Override
    public String description() {
        return "MDM 银行网点接收：写入 BT_INPUT_BANK_INFO，只收境内银行（INSIDE）";
    }

    @Override
    public boolean rawBody() {
        return true;
    }

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        return service.receiveBankBranch(body);
    }
}
