package com.interchange.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全局禁用浏览器缓存。
 *
 * 背景（真实踩过的坑）：本平台的页面与前端 JS 更新后，浏览器若沿用缓存副本，
 * 会出现"后端明明已修好、用户仍看到旧行为"的假故障——
 * 登录页 fetch 写法修复后，缓存里的旧 login.html / app.js 依旧报"请输入用户名"。
 *
 * 本平台是内部系统，对带宽不敏感，直接全面禁用缓存，
 * 换取"刷新即最新"，同时避免排查时被缓存误导。
 */
@Component
public class NoCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        chain.doFilter(request, response);
    }
}
