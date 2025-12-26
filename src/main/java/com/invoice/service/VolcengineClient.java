package com.invoice.service;

import com.invoice.config.VolcengineConfig;
import com.volcengine.ark.runtime.service.ArkService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VolcengineClient {

    private final ArkService arkService;

    @Autowired
    public VolcengineClient(VolcengineConfig config) {
        // 初始化Ark客户端
        String apiKey = config.getArkApiKey();
        String baseUrl = config.getBaseUrl();

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("ARK_API_KEY 未配置，请设置环境变量或配置文件");
        }

        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        this.arkService = ArkService.builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        
        log.info("火山引擎客户端初始化成功，Base URL: {}", baseUrl);
    }

    public ArkService getArkService() {
        return arkService;
    }

    public void shutdown() {
        if (arkService != null) {
            arkService.shutdownExecutor();
        }
    }
}



