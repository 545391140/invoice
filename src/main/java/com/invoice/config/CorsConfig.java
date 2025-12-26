package com.invoice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")  // 使用 allowedOriginPatterns 替代 allowedOrigins，支持 allowCredentials
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            .allowedHeaders("*")
            .exposedHeaders("*")  // 暴露所有响应头
            .allowCredentials(true)
            .maxAge(3600);
    }
}


