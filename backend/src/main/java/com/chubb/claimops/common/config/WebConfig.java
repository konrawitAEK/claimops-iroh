package com.chubb.claimops.common.config;

import com.chubb.claimops.common.auth.CurrentUserArgumentResolver;
import com.chubb.claimops.common.auth.HeaderAuthInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers header-based identity for every {@code /api/**} route and allows the local Angular dev server. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final HeaderAuthInterceptor headerAuthInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfig(HeaderAuthInterceptor headerAuthInterceptor, CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.headerAuthInterceptor = headerAuthInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(headerAuthInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST")
                .allowedHeaders(HeaderAuthInterceptor.ROLE_HEADER, HeaderAuthInterceptor.NAME_HEADER, "Content-Type");
    }
}
