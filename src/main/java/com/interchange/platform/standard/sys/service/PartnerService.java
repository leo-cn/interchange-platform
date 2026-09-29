package com.interchange.platform.standard.sys.service;

import com.interchange.platform.standard.exception.BizException;
import com.interchange.platform.standard.utils.Crypto;
import com.interchange.platform.standard.utils.Utils;
import com.interchange.platform.standard.sys.entity.Partner;
import com.interchange.platform.standard.sys.dao.InterfaceTaskDao;
import com.interchange.platform.standard.sys.dao.PartnerDao;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 第三方系统（对接方）管理。密钥加密存储，页面只回显掩码。
 */
@Service
public class PartnerService {

    @Resource
    private PartnerDao partnerDao;
    @Resource
    private InterfaceTaskDao taskDao;

    public List<Partner> listAll() {
        return partnerDao.findAllDesc();
    }

    public List<Partner> listEnabled() {
        return partnerDao.findByStatus(1);
    }

    public Partner get(Long id) {
        return partnerDao.get(id, "第三方系统不存在: " + id);
    }

    @Transactional
    public Partner save(Partner form) {
        if (form.getPartnerCode() == null || form.getPartnerCode().isBlank()) {
            throw new BizException(400, "系统编码不能为空");
        }
        if (form.getPartnerName() == null || form.getPartnerName().isBlank()) {
            throw new BizException(400, "系统名称不能为空");
        }
        if (form.getBaseUrl() == null || form.getBaseUrl().isBlank()) {
            throw new BizException(400, "服务地址 baseUrl 不能为空");
        }
        if (!form.getBaseUrl().startsWith("http")) {
            throw new BizException(400, "服务地址必须以 http:// 或 https:// 开头");
        }
        // 请求头 JSON 校验
        DataFetcherService.parseHeaders(form.getHeadersJson());

        Partner entity;
        boolean isNew = form.getId() == null;
        if (isNew) {
            partnerDao.findByPartnerCode(form.getPartnerCode().trim()).ifPresent(p -> {
                throw new BizException(400, "系统编码已存在: " + p.getPartnerCode());
            });
            entity = new Partner();
            entity.setPartnerCode(form.getPartnerCode().trim());
        } else {
            entity = get(form.getId());
        }

        entity.setPartnerName(form.getPartnerName().trim());
        entity.setBaseUrl(form.getBaseUrl().trim());
        entity.setAuthType(form.getAuthType() == null ? "NONE" : form.getAuthType());
        entity.setAuthUser(form.getAuthUser());
        entity.setHeadersJson(form.getHeadersJson());
        entity.setTimeoutMs(form.getTimeoutMs());
        entity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        entity.setRemark(form.getRemark());

        // 密钥：前端回传空或掩码时保留原值，否则加密保存
        String secret = form.getAuthSecret();
        if (secret == null || secret.isBlank() || "******".equals(secret)) {
            if (isNew) {
                entity.setAuthSecret(null);
            }
        } else {
            entity.setAuthSecret(Crypto.encrypt(secret));
        }
        return partnerDao.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        Partner partner = get(id);
        long refs = taskDao.findByPartnerId(id).size();
        if (refs > 0) {
            throw new BizException(400, "该第三方系统已被 " + refs + " 个任务引用，请先解除引用");
        }
        partnerDao.delete(partner);
    }

    @Transactional
    public void toggle(Long id, boolean enabled) {
        Partner partner = get(id);
        partner.setStatus(enabled ? 1 : 0);
        partnerDao.save(partner);
    }

    /** 供页面回显：密钥掩码 */
    public String maskSecret(Partner partner) {
        String secret = Crypto.decrypt(partner.getAuthSecret());
        return secret == null || secret.isBlank() ? "" : "******";
    }

    /** 修改时间展示 */
    public String formatTime(java.time.LocalDateTime time) {
        return Utils.format(time);
    }
}
