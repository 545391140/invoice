package com.invoice.service;

import com.invoice.dto.InvoiceRecognizeResponse;
import com.invoice.dto.TaskStatusResponse;
import com.invoice.model.InvoiceInfo;
import com.invoice.util.ApiResponseParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.springframework.mock.web.MockMultipartFile;

@Slf4j
@Service
public class InvoiceService {
    
    private final Path originalStorageLocation;
    private final Path croppedStorageLocation;
    private final Path tempStorageLocation;
    
    private final PdfProcessor pdfProcessor;
    private final VolcengineApiService apiService;
    private final ImageCropService imageCropService;
    private final ApiResponseParser responseParser;
    
    // 异步任务存储（生产环境应使用Redis或数据库）
    private final Map<String, TaskStatusResponse> taskStore = new ConcurrentHashMap<>();
    
    @Autowired
    public InvoiceService(
            @Value("${app.upload-folder:uploads}") String uploadFolder,
            @Value("${app.output-folder:outputs}") String outputFolder,
            @Value("${app.temp-folder:temp}") String tempFolder,
            PdfProcessor pdfProcessor,
            VolcengineApiService apiService,
            ImageCropService imageCropService,
            ApiResponseParser responseParser) {
        
        this.originalStorageLocation = Paths.get(uploadFolder, "original").toAbsolutePath().normalize();
        this.croppedStorageLocation = Paths.get(outputFolder).toAbsolutePath().normalize();
        this.tempStorageLocation = Paths.get(tempFolder).toAbsolutePath().normalize();
        
        this.pdfProcessor = pdfProcessor;
        this.apiService = apiService;
        this.imageCropService = imageCropService;
        this.responseParser = responseParser;
        
        try {
            Files.createDirectories(this.originalStorageLocation);
            Files.createDirectories(this.croppedStorageLocation);
            Files.createDirectories(this.tempStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("无法创建存储目录", ex);
        }
    }
    
    /**
     * 同步识别与裁切发票
     */
    public InvoiceRecognizeResponse recognizeAndCrop(MultipartFile file, 
                                                    int cropPadding, 
                                                    String outputFormat) throws Exception {
        long startTime = System.currentTimeMillis();
        String taskId = UUID.randomUUID().toString();
        
        log.info("开始处理文件: {}, taskId: {}", file.getOriginalFilename(), taskId);
        
        try {
            // 1. 保存原始文件
            String originalFilename = saveOriginalFile(file, taskId);
            
            // 2. 判断文件类型并处理
            String contentType = file.getContentType();
            List<BufferedImage> images;
            List<InvoiceInfo> allInvoices = new ArrayList<>();
            
            if (contentType != null && contentType.equals("application/pdf")) {
                // PDF 处理
                String pdfPath = originalStorageLocation.resolve(originalFilename).toString();
                images = pdfProcessor.pdfToBufferedImages(pdfPath);
                
                // 保存PDF转换后的图片
                for (int i = 0; i < images.size(); i++) {
                    byte[] imageBytes = bufferedImageToBytes(images.get(i));
                    saveOriginalImage(imageBytes, taskId, i + 1);
                }
                
                // 处理每一页
                for (int pageIndex = 0; pageIndex < images.size(); pageIndex++) {
                    BufferedImage image = images.get(pageIndex);
                    int page = pageIndex + 1;
                    
                    // 保存临时图片用于API调用
                    String tempImagePath = saveTempImage(image, taskId, page);
                    
                    // 调用API识别
                    String apiResponse = apiService.callVolcengineVisionApi(tempImagePath);
                    List<Map<String, Object>> invoices = responseParser.parseApiResponse(apiResponse);
                    
                    // 设置页码
                    for (Map<String, Object> invoice : invoices) {
                        invoice.put("page", page);
                    }
                    
                    // 裁切发票
                    List<InvoiceInfo> pageInvoices = cropInvoicesFromImage(
                        image, invoices, taskId, page, cropPadding, outputFormat);
                    allInvoices.addAll(pageInvoices);
                }
            } else {
                // 图片处理
                BufferedImage image = ImageIO.read(file.getInputStream());
                if (image == null) {
                    throw new IllegalArgumentException("无法读取图片文件");
                }
                
                images = Collections.singletonList(image);
                byte[] imageBytes = bufferedImageToBytes(image);
                saveOriginalImage(imageBytes, taskId, 1);
                
                // 保存临时图片
                String tempImagePath = saveTempImage(image, taskId, 1);
                
                // 调用API识别
                String apiResponse = apiService.callVolcengineVisionApi(tempImagePath);
                List<Map<String, Object>> invoices = responseParser.parseApiResponse(apiResponse);
                
                // 设置页码
                for (Map<String, Object> invoice : invoices) {
                    invoice.put("page", 1);
                }
                
                // 裁切发票
                allInvoices = cropInvoicesFromImage(image, invoices, taskId, 1, cropPadding, outputFormat);
            }
            
            // 构建响应
            InvoiceRecognizeResponse response = new InvoiceRecognizeResponse();
            response.setTaskId(taskId);
            response.setTotalInvoices(allInvoices.size());
            response.setInvoices(allInvoices);
            response.setProcessingTime((System.currentTimeMillis() - startTime) / 1000.0);
            
            log.info("处理完成，识别到 {} 张发票，耗时: {} 秒", 
                allInvoices.size(), response.getProcessingTime());
            
            return response;
            
        } catch (Exception e) {
            log.error("处理失败", e);
            throw e;
        }
    }
    
    /**
     * 提交异步任务
     */
    public String submitAsyncTask(MultipartFile file, int cropPadding, String outputFormat) {
        String taskId = UUID.randomUUID().toString();
        
        TaskStatusResponse taskStatus = new TaskStatusResponse();
        taskStatus.setTaskId(taskId);
        taskStatus.setStatus("PENDING");
        taskStatus.setProgress(0);
        taskStatus.setCreatedAt(Instant.now().toString());
        taskStore.put(taskId, taskStatus);
        
        // 保存文件到临时目录（因为 MultipartFile 的流在请求结束后会关闭）
        String tempFilePath;
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String tempFilename = String.format("%s_temp.%s", taskId, extension);
            Path tempFile = tempStorageLocation.resolve(tempFilename);
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFilePath = tempFile.toString();
            log.info("已保存临时文件: {}", tempFilePath);
        } catch (IOException e) {
            log.error("保存临时文件失败", e);
            taskStatus.setStatus("FAILED");
            throw new RuntimeException("保存文件失败", e);
        }
        
        // 异步处理
        final String finalTempFilePath = tempFilePath;
        final String finalContentType = file.getContentType();
        final String finalOriginalFilename = file.getOriginalFilename();
        
        CompletableFuture.supplyAsync(() -> {
            try {
                taskStatus.setStatus("PROCESSING");
                taskStatus.setProgress(10);
                
                // 从临时文件重新创建 MultipartFile
                Path tempFile = Paths.get(finalTempFilePath);
                byte[] fileBytes = Files.readAllBytes(tempFile);
                String contentType = finalContentType != null ? finalContentType : "application/octet-stream";
                
                // 创建临时 MultipartFile
                MockMultipartFile tempMultipartFile = new MockMultipartFile(
                    "file", finalOriginalFilename, contentType, fileBytes
                );
                
                InvoiceRecognizeResponse result = recognizeAndCrop(
                    tempMultipartFile, cropPadding, outputFormat);
                
                taskStatus.setStatus("COMPLETED");
                taskStatus.setProgress(100);
                taskStatus.setTotalInvoices(result.getTotalInvoices());
                taskStatus.setInvoices(result.getInvoices());
                taskStatus.setCompletedAt(Instant.now().toString());
                
                // 清理临时文件
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("删除临时文件失败: {}", finalTempFilePath);
                }
                
                return result;
            } catch (Exception e) {
                log.error("异步任务处理失败", e);
                taskStatus.setStatus("FAILED");
                taskStatus.setProgress(0);
                // 清理临时文件
                try {
                    Files.deleteIfExists(Paths.get(finalTempFilePath));
                } catch (IOException ex) {
                    log.warn("删除临时文件失败: {}", finalTempFilePath);
                }
                return null;
            }
        });
        
        return taskId;
    }
    
    /**
     * 查询任务状态
     */
    public TaskStatusResponse getTaskStatus(String taskId) {
        TaskStatusResponse taskStatus = taskStore.get(taskId);
        if (taskStatus == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return taskStatus;
    }
    
    /**
     * 从图片中裁切多张发票
     */
    private List<InvoiceInfo> cropInvoicesFromImage(BufferedImage image,
                                                   List<Map<String, Object>> invoices,
                                                   String taskId,
                                                   int page,
                                                   int padding,
                                                   String outputFormat) throws IOException {
        List<InvoiceInfo> result = new ArrayList<>();
        int width = image.getWidth();
        int height = image.getHeight();
        
        for (int idx = 0; idx < invoices.size(); idx++) {
            Map<String, Object> invoiceData = invoices.get(idx);
            try {
                @SuppressWarnings("unchecked")
                List<Integer> bbox = (List<Integer>) invoiceData.get("bbox");
                Double confidence = (Double) invoiceData.getOrDefault("confidence", 0.9);
                
                // 裁切发票
                String filename = String.format("invoice_%d_%d.%s", page, idx, outputFormat);
                String outputPath = croppedStorageLocation.resolve(filename).toString();
                
                BufferedImage cropped = imageCropService.cropInvoice(image, bbox, padding, outputPath);
                
                // 创建发票信息
                InvoiceInfo invoiceInfo = new InvoiceInfo();
                invoiceInfo.setIndex(idx);
                invoiceInfo.setPage(page);
                invoiceInfo.setBbox(bbox);
                invoiceInfo.setConfidence(confidence);
                invoiceInfo.setFilename(filename);
                
                result.add(invoiceInfo);
                
            } catch (Exception e) {
                log.warn("裁切发票失败，索引: {}, 错误: {}", idx, e.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * 保存原始文件
     */
    private String saveOriginalFile(MultipartFile file, String taskId) throws IOException {
        String extension = getFileExtension(file.getOriginalFilename());
        String filename = String.format("%s_original.%s", taskId, extension);
        Path targetLocation = originalStorageLocation.resolve(filename);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        return filename;
    }
    
    /**
     * 保存原始图片
     */
    private String saveOriginalImage(byte[] imageData, String taskId, int page) throws IOException {
        String filename = String.format("%s_page_%d.jpg", taskId, page);
        Path targetLocation = originalStorageLocation.resolve(filename);
        Files.write(targetLocation, imageData);
        return filename;
    }
    
    /**
     * 保存临时图片
     */
    private String saveTempImage(BufferedImage image, String taskId, int page) throws IOException {
        String filename = String.format("%s_temp_%d.jpg", taskId, page);
        Path targetLocation = tempStorageLocation.resolve(filename);
        ImageIO.write(image, "jpg", targetLocation.toFile());
        return targetLocation.toString();
    }
    
    /**
     * BufferedImage 转字节数组
     */
    private byte[] bufferedImageToBytes(BufferedImage image) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }
    
    /**
     * 获取原始图片资源
     */
    public Resource getOriginalImageResource(String taskId, int page) throws IOException {
        String filename = String.format("%s_page_%d.jpg", taskId, page);
        Path filePath = originalStorageLocation.resolve(filename).normalize();
        
        if (!Files.exists(filePath)) {
            // 尝试查找原始文件
            try (var stream = Files.list(originalStorageLocation)) {
                filePath = stream
                    .filter(path -> path.getFileName().toString().startsWith(taskId + "_original"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("原始文件不存在"));
            }
        }
        
        Resource resource = new UrlResource(filePath.toUri());
        if (resource.exists()) {
            return resource;
        } else {
            throw new IOException("文件不存在: " + filename);
        }
    }
    
    /**
     * 获取裁切后的图片资源
     */
    public Resource getCroppedImageResource(String filename) throws IOException {
        Path filePath = croppedStorageLocation.resolve(filename).normalize();
        Resource resource = new UrlResource(filePath.toUri());
        if (resource.exists()) {
            return resource;
        } else {
            throw new IOException("文件不存在: " + filename);
        }
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "bin";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "bin";
    }
}

