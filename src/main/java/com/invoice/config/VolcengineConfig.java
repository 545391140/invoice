package com.invoice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "volcengine")
@Data
public class VolcengineConfig {
    private String arkApiKey;
    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
    private ModelConfig model;

    public String getArkApiKey() {
        // 优先从环境变量读取
        return arkApiKey != null && !arkApiKey.isEmpty() 
            ? arkApiKey 
            : System.getenv("ARK_API_KEY");
    }

    @Data
    public static class ModelConfig {
        private String name = "doubao-seed-1-6-vision-250815";
    }
}

