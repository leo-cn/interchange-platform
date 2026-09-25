package com.interchange.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：注册登录拦截器，并放行登录页、静态资源、H2 控制台与对外接收接口。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/doLogin",
                        "/logout",
                        "/favicon.ico",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/error",
                        "/h2-console/**",
                        // 对外接收第三方请求的接口，走 Token 鉴权，不做会话校验
                        "/api/receive/**",
                        // 令牌签发 / 注销：第三方用 appKey+appSecret 换 access_token，本身不能要求先登录
                        "/api/oauth/**",
                        // 内置的“模拟第三方”接口，用于联调自测，生产环境可删除
                        "/api/mock/**",
                        // 平台自身健康检查（供监控探活，无需登录）
                        "/api/system/health"
                );
    }
}
