package com.interchange.platform.service;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.Crypto;
import com.interchange.platform.common.LoginUser;
import com.interchange.platform.config.AppProps;
import com.interchange.platform.entity.SysUserExt;
import com.interchange.platform.repository.SysUserExtRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 登录鉴权与用户授权。
 *
 * <p><strong>平台没有自己的用户表</strong>：账号主数据（登录名、姓名、口令、启停）
 * 来自业务系统用户表，平台只在 sys_user_ext 上挂角色 / 接口令牌 / 平台口令。
 * 一份账号两边共用，不存在两份数据打架的问题。
 *
 * <p>口令判定顺序：
 * <ol>
 *   <li>在平台改过密码（platform_password 有值）→ 只认 BCrypt；</li>
 *   <li>没改过且 {@code app.user.allow-biz-password=true} → 用业务系统口令
 *       （MD5(salt+明文)）登录；</li>
 *   <li>都没命中 → 拒绝。</li>
 * </ol>
 * 平台改密码<strong>不会</strong>回写业务库，业务系统的口令该是什么还是什么。
 */
@Service
public class AuthService {

    private final SysUserExtRepository extRepository;
    private final BizUserDirectory directory;
    private final AppProps appProps;

    public AuthService(SysUserExtRepository extRepository,
                       BizUserDirectory directory,
                       AppProps appProps) {
        this.extRepository = extRepository;
        this.directory = directory;
        this.appProps = appProps;
    }

    @Transactional
    public LoginUser login(String loginName, String password) {
        if (loginName == null || loginName.isBlank() || password == null || password.isBlank()) {
            throw new BizException(400, "请输入用户名和密码");
        }
        BizUserDirectory.BizUser biz = directory.findByLoginName(loginName)
                .orElseThrow(() -> new BizException(401, "用户名或密码错误"));
        if (!directory.isActive(biz)) {
            throw new BizException(403, "该账号在业务系统中已被停用，请联系管理员");
        }

        SysUserExt ext = extRepository.findByExtId(biz.id()).orElse(null);
        if (ext == null) {
            if (!appProps.getUser().isAutoProvision()) {
                throw new BizException(403, "该账号尚未开通平台访问权限，请联系管理员");
            }
            ext = newExt(biz.id());
        }
        if (ext.getStatus() == null || ext.getStatus() != 1) {
            throw new BizException(403, "该账号的平台访问权限已被停用，请联系管理员");
        }
        if (!checkPassword(biz, ext, password)) {
            throw new BizException(401, "用户名或密码错误");
        }

        ext.setLastLoginTime(LocalDateTime.now());
        SysUserExt saved = extRepository.save(ext);
        return new LoginUser(biz.id(), biz.loginName(), biz.username(), saved.getRole());
    }

    /**
     * 修改本人在<strong>本平台</strong>的登录口令。
     * 原口令可以是平台口令，也可以是业务系统口令（没设过平台口令时）。
     */
    @Transactional
    public void changePassword(String userId, String oldPassword, String newPassword) {
        BizUserDirectory.BizUser biz = directory.findById(userId)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        SysUserExt ext = extRepository.findByExtId(userId).orElseGet(() -> newExt(userId));
        if (!checkPassword(biz, ext, oldPassword)) {
            throw new BizException(400, "原密码不正确");
        }
        checkPasswordStrength(newPassword);
        ext.setPlatformPassword(Crypto.hashPassword(newPassword));
        extRepository.save(ext);
    }

    /** 平台视角的账号清单：业务系统账号 + 平台授权情况（无 ext 记录 = 还没登过平台） */
    public List<UserView> listUsers() {
        Map<String, SysUserExt> exts = new HashMap<>();
        extRepository.findAll().forEach(e -> {
            if (e.getExtId() != null) {
                exts.put(e.getExtId(), e);
            }
        });
        List<UserView> list = new ArrayList<>();
        for (BizUserDirectory.BizUser biz : directory.listAll()) {
            SysUserExt ext = exts.get(biz.id());
            list.add(new UserView(
                    biz.id(),
                    biz.loginName(),
                    biz.displayName(),
                    biz.corpId(),
                    biz.status(),
                    ext == null ? null : ext.getRole(),
                    ext == null ? null : ext.getStatus(),
                    ext == null ? null : ext.getLastLoginTime()));
        }
        return list;
    }

    /** 查询用户当前的接口令牌 */
    public String apiToken(String userId) {
        Optional<SysUserExt> ext = extRepository.findByExtId(userId);
        return ext.map(SysUserExt::getApiToken).orElse(null);
    }

    @Transactional
    public String resetApiToken(String userId) {
        SysUserExt ext = extRepository.findByExtId(userId).orElseGet(() -> newExt(userId));
        ext.setApiToken(newToken());
        return extRepository.save(ext).getApiToken();
    }

    /** 账号在平台上的授权视图 */
    public record UserView(String id,
                           String loginName,
                           String displayName,
                           String corpId,
                           Integer bizStatus,
                           String role,
                           Integer platformStatus,
                           LocalDateTime lastLoginTime) {
    }

    private boolean checkPassword(BizUserDirectory.BizUser biz, SysUserExt ext, String raw) {
        if (raw == null) {
            return false;
        }
        if (ext.getPlatformPassword() != null && !ext.getPlatformPassword().isBlank()) {
            return Crypto.matchPassword(raw, ext.getPlatformPassword());
        }
        if (!appProps.getUser().isAllowBizPassword()) {
            return false;
        }
        return Crypto.matchBizPassword(raw, biz.salt(), biz.passwordHash());
    }

    private SysUserExt newExt(String bizUserId) {
        SysUserExt ext = new SysUserExt();
        ext.setExtId(bizUserId);
        String role = appProps.getUser().getDefaultRole();
        ext.setRole(role == null || role.isBlank() ? "OPERATOR" : role.trim());
        ext.setStatus(1);
        ext.setApiToken(newToken());
        return ext;
    }

    private String newToken() {
        return "PO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private void checkPasswordStrength(String password) {
        if (password == null || password.length() < 6) {
            throw new BizException(400, "密码长度不能少于 6 位");
        }
    }
}
