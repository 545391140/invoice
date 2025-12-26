package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.AsyncTaskResponse;
import com.invoice.dto.HealthResponse;
import com.invoice.dto.InvoiceRecognizeResponse;
import com.invoice.dto.TaskStatusResponse;
import com.invoice.service.InvoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping("/api/v1/invoice")
// CORS 配置已在 CorsConfig 中统一处理，不需要在这里重复配置
public class InvoiceController {
    
    @Autowired
    private InvoiceService invoiceService;
    
    /**
     * 同步识别与裁切
     */
    @PostMapping(value = "/recognize-and-crop", 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<InvoiceRecognizeResponse>> recognizeAndCrop(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "cropPadding", defaultValue = "10") Integer cropPadding,
            @RequestParam(value = "outputFormat", defaultValue = "jpg") String outputFormat) {
        
        try {
            log.info("收到识别请求 - 文件名: {}, 大小: {} bytes, cropPadding: {}, outputFormat: {}", 
                file.getOriginalFilename(), file.getSize(), cropPadding, outputFormat);
            
            // 验证文件
            if (file == null || file.isEmpty()) {
                log.warn("文件为空或未提供");
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "文件不能为空"));
            }
            
            // 调用服务
            InvoiceRecognizeResponse response = invoiceService.recognizeAndCrop(
                file, cropPadding, outputFormat);
            
            // 为每个发票添加预览URL和下载URL
            String taskId = response.getTaskId();
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                throw new IllegalStateException("无法获取请求上下文");
            }
            HttpServletRequest request = attributes.getRequest();
            String baseUrl = request.getScheme() + "://" + request.getServerName() 
                + ":" + request.getServerPort() + "/api/v1/invoice";
            
            response.getInvoices().forEach(invoice -> {
                // 设置裁切后图片预览URL
                String croppedPreviewUrl = String.format("%s/preview/cropped/%s", 
                    baseUrl, invoice.getFilename());
                invoice.setImageUrl(croppedPreviewUrl);
                
                // 设置裁切后图片下载URL
                String croppedDownloadUrl = String.format("%s/download/%s", 
                    baseUrl, invoice.getFilename());
                invoice.setDownloadUrl(croppedDownloadUrl);
                
                // 设置原始图片预览URL
                String originalPreviewUrl = String.format("%s/preview/original/%s?page=%d", 
                    baseUrl, taskId, invoice.getPage());
                invoice.setOriginalImageUrl(originalPreviewUrl);
            });
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("识别失败", e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error(500, "处理失败: " + e.getMessage()));
        }
    }
    
    /**
     * 异步识别与裁切
     */
    @PostMapping(value = "/recognize-and-crop/async",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AsyncTaskResponse>> recognizeAndCropAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "cropPadding", defaultValue = "10") Integer cropPadding,
            @RequestParam(value = "outputFormat", defaultValue = "jpg") String outputFormat) {
        
        try {
            String taskId = invoiceService.submitAsyncTask(file, cropPadding, outputFormat);
            AsyncTaskResponse response = new AsyncTaskResponse();
            response.setTaskId(taskId);
            response.setStatus("PROCESSING");
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("提交任务失败", e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error(500, "提交任务失败: " + e.getMessage()));
        }
    }
    
    /**
     * 查询任务状态
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<TaskStatusResponse>> getTaskStatus(
            @PathVariable String taskId) {
        
        try {
            TaskStatusResponse response = invoiceService.getTaskStatus(taskId);
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("查询任务状态失败", e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error(500, "查询失败: " + e.getMessage()));
        }
    }
    
    /**
     * 预览原始图片
     */
    @GetMapping("/preview/original/{taskId}")
    public ResponseEntity<Resource> previewOriginalImage(
            @PathVariable String taskId,
            @RequestParam(value = "page", defaultValue = "1") Integer page) {
        try {
            Resource resource = invoiceService.getOriginalImageResource(taskId, page);
            return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/jpeg"))
                .header("Cache-Control", "public, max-age=3600")
                .body(resource);
                
        } catch (Exception e) {
            log.error("预览原始图片失败", e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 预览裁切后的图片
     */
    @GetMapping("/preview/cropped/{filename}")
    public ResponseEntity<Resource> previewCroppedImage(@PathVariable String filename) {
        try {
            Resource resource = invoiceService.getCroppedImageResource(filename);
            return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/jpeg"))
                .header("Cache-Control", "public, max-age=3600")
                .body(resource);
                
        } catch (Exception e) {
            log.error("预览裁切图片失败", e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 下载裁切后的图片
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadCroppedImage(@PathVariable String filename) {
        try {
            Resource resource = invoiceService.getCroppedImageResource(filename);
            return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/jpeg"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(resource);
                
        } catch (Exception e) {
            log.error("下载图片失败", e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 下载原始图片
     */
    @GetMapping("/download/original/{taskId}")
    public ResponseEntity<Resource> downloadOriginalImage(
            @PathVariable String taskId,
            @RequestParam(value = "page", defaultValue = "1") Integer page) {
        try {
            Resource resource = invoiceService.getOriginalImageResource(taskId, page);
            String filename = String.format("original_page_%d.jpg", page);
            return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/jpeg"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(resource);
                
        } catch (Exception e) {
            log.error("下载原始图片失败", e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        HealthResponse health = new HealthResponse();
        health.setStatus("UP");
        health.setVersion("1.0.0");
        return ResponseEntity.ok(ApiResponse.success(health));
    }
}

