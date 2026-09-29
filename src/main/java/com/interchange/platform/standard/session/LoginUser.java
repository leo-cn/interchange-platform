package com.interchange.platform.standard.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录态用户，保存在 HttpSession 中。
 *
 * <p>id 是<strong>业务系统用户表的主键</strong>（sys_user.ID，32 位字符串），
 * 平台不另建用户表，所以这里不再是自增 Long。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private String id;
    private String username;
    private String realName;
    private String role;

    /** 展示名：优先真实姓名 */
    public String getDisplayName() {
        return (realName == null || realName.isBlank()) ? username : realName;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    /** 只读用户不能改配置、不能手工触发 */
    public boolean isViewer() {
        return "VIEWER".equalsIgnoreCase(role);
    }
}
