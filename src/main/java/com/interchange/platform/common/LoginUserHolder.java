package com.interchange.platform.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 登录态存取工具。会话键统一为 {@link #SESSION_KEY}。
 */
public final class LoginUserHolder {

    public static final String SESSION_KEY = "LOGIN_USER";

    private LoginUserHolder() {
    }

    public static void save(HttpSession session, LoginUser user) {
        session.setAttribute(SESSION_KEY, user);
    }

    public static LoginUser get(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object obj = session.getAttribute(SESSION_KEY);
        return obj instanceof LoginUser ? (LoginUser) obj : null;
    }

    public static void clear(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
        }
    }

    /** 当前请求的登录用户，未登录返回 null */
    public static LoginUser current() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return get(request.getSession(false));
    }
}
