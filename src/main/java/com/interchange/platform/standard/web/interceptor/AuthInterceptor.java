package com.interchange.platform.standard.web.interceptor;

import com.interchange.platform.standard.sys.vo.ResultDTO;

import com.interchange.platform.standard.session.LoginUser;
import com.interchange.platform.standard.session.LoginUserHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interchange.platform.standard.utils.JsonUtil;
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

    private static final ObjectMapper MAPPER = JsonUtil.getMapper();

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
            ResultDTO<Void> body = ResultDTO.fail(401, "登录已失效，请重新登录");
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
