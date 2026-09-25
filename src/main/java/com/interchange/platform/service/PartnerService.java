package com.interchange.platform.service;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.Crypto;
import com.interchange.platform.common.Utils;
import com.interchange.platform.entity.Partner;
import com.interchange.platform.repository.InterfaceTaskRepository;
import com.interchange.platform.repository.PartnerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 第三方系统（对接方）管理。密钥加密存储，页面只回显掩码。
 */
@Service
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final InterfaceTaskRepository taskRepository;

    public PartnerService(PartnerRepository partnerRepository, InterfaceTaskRepository taskRepository) {
        this.partnerRepository = partnerRepository;
        this.taskRepository = taskRepository;
    }

    public List<Partner> listAll() {
        return partnerRepository.findAllByOrderByIdDesc();
    }

    public List<Partner> listEnabled() {
        return partnerRepository.findByStatusOrderByIdAsc(1);
    }

    public Partner get(Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "第三方系统不存在: " + id));
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
        DataFetcher.parseHeaders(form.getHeadersJson());

        Partner entity;
        boolean isNew = form.getId() == null;
        if (isNew) {
            partnerRepository.findByPartnerCode(form.getPartnerCode().trim()).ifPresent(p -> {
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
        return partnerRepository.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        Partner partner = get(id);
        long refs = taskRepository.findByPartnerId(id).size();
        if (refs > 0) {
            throw new BizException(400, "该第三方系统已被 " + refs + " 个任务引用，请先解除引用");
        }
        partnerRepository.delete(partner);
    }

    @Transactional
    public void toggle(Long id, boolean enabled) {
        Partner partner = get(id);
        partner.setStatus(enabled ? 1 : 0);
        partnerRepository.save(partner);
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
