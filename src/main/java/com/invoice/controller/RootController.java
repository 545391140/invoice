package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestController
public class RootController {
    
    /**
     * API 信息
     */
    @GetMapping("/api/v1/info")
    public ResponseEntity<ApiResponse<Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "Invoice Auto Crop Service");
        info.put("version", "1.0.0");
        info.put("description", "发票自动识别与裁切服务");
        info.put("endpoints", Arrays.asList(
            "POST /api/v1/invoice/recognize-and-crop - 同步识别与裁切",
            "POST /api/v1/invoice/recognize-and-crop/async - 异步识别与裁切",
            "GET /api/v1/invoice/task/{taskId} - 查询任务状态",
            "GET /api/v1/invoice/preview/original/{taskId}?page=1 - 预览原始图片",
            "GET /api/v1/invoice/preview/cropped/{filename} - 预览裁切后的图片",
            "GET /api/v1/invoice/download/{filename} - 下载裁切后的图片",
            "GET /api/v1/invoice/download/original/{taskId}?page=1 - 下载原始图片",
            "GET /api/v1/invoice/health - 健康检查"
        ));
        return ResponseEntity.ok(ApiResponse.success(info));
    }
}




