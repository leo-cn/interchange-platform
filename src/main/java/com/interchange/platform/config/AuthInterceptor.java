package com.interchange.platform.config;

import com.interchange.platform.common.LoginUser;
import com.interchange.platform.common.LoginUserHolder;
import com.interchange.platform.common.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器。
 * <ul>
 *   <li>页面请求未登录 → 302 跳登录页；</li>
 *   <li>AJAX 请求未登录 → 返回 JSON 提示 401，前端统一跳转。</li>
 * </ul>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        HttpSession session = request.getSession(false);
        LoginUser user = LoginUserHolder.get(session);
        if (user != null) {
            return true;
        }

        if (isAjax(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            R<Void> body = R.fail(401, "登录已失效，请重新登录");
            response.getWriter().write(MAPPER.writeValueAsString(body));
        } else {
            response.sendRedirect(request.getContextPath() + "/login?redirect="
                    + java.net.URLEncoder.encode(request.getRequestURI(), StandardCharsets.UTF_8));
        }
        return false;
    }

    private boolean isAjax(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        return "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && accept.contains("application/json"))
                || uri.startsWith("/api/");
    }
}
