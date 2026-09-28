package com.interchange.platform.mdm.handler;

import com.interchange.platform.mdm.constant.MdmConstant;
import com.interchange.platform.mdm.MdmReceiveService;
import com.interchange.platform.service.handler.ReceiveHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 组织机构接收： URL {@code POST /api/receive/mdm-org-receive}。
 *
 * <p>写入 SYS_CORP，<b>只新增不更新</b>：同一个 mdId 第二次推会被判 FAIL「重复接收」，
 * 与 dyg-erp 原逻辑一致，避免覆盖已经挂了大量业务数据的单位。
 */
@Component
public class OrgReceiveHandler implements ReceiveHandler {

    private final MdmReceiveService service;

    public OrgReceiveHandler(MdmReceiveService service) {
        this.service = service;
    }

    @Override
    public String apiCode() {
        return MdmConstant.API_ORG;
    }

    @Override
    public String description() {
        return "MDM 组织机构接收：写入 SYS_CORP，仅新增不更新，重复 mdId 判重";
    }

    @Override
    public boolean rawBody() {
        return true;
    }

    @Override
    public Map<String, Object> handle(String body, String traceId, Map<String, String> headers) {
        return service.receiveOrg(body);
    }
}
