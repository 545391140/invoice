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
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.IIOImage;
import javax.imageio.stream.FileImageOutputStream;
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
                    int imageWidth = image.getWidth();
                    int imageHeight = image.getHeight();
                    log.info("调用API识别，使用临时图片: {}, BufferedImage尺寸: {}x{}, 页码: {}", 
                        tempImagePath, imageWidth, imageHeight, page);
                    String apiResponse = apiService.callVolcengineVisionApi(tempImagePath);
                    List<Map<String, Object>> invoices = responseParser.parseApiResponse(apiResponse);
                    
                    log.info("API返回发票数量: {}, 用于裁切的BufferedImage尺寸: {}x{} (应与API识别的图片尺寸一致)", 
                        invoices.size(), imageWidth, imageHeight);
                    
                    // 检查并缩放坐标（如果API返回的是归一化坐标0-999/1000）
                    normalizeBboxCoordinates(invoices, page, imageWidth, imageHeight);
                    
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
                int imageWidth = image.getWidth();
                int imageHeight = image.getHeight();
                log.info("调用API识别，使用临时图片: {}, BufferedImage尺寸: {}x{}", 
                    tempImagePath, imageWidth, imageHeight);
                String apiResponse = apiService.callVolcengineVisionApi(tempImagePath);
                List<Map<String, Object>> invoices = responseParser.parseApiResponse(apiResponse);
                
                log.info("API返回发票数量: {}, 用于裁切的BufferedImage尺寸: {}x{} (应与API识别的图片尺寸一致)", 
                    invoices.size(), imageWidth, imageHeight);
                
                // 检查并缩放坐标（如果API返回的是归一化坐标0-999/1000）
                normalizeBboxCoordinates(invoices, 1, imageWidth, imageHeight);
                
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
                // 注意：MockMultipartFile是spring-test包中的类，在生产代码中使用它虽然可行（实现了MultipartFile接口），
                // 但更好的做法是创建自定义的MultipartFile实现。这里使用MockMultipartFile是为了简化代码。
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
        log.debug("开始裁切发票，图片尺寸: {}x{}, 发票数量: {}", 
            image.getWidth(), image.getHeight(), invoices.size());
        
        for (int idx = 0; idx < invoices.size(); idx++) {
            Map<String, Object> invoiceData = invoices.get(idx);
            try {
                @SuppressWarnings("unchecked")
                List<Integer> bbox = (List<Integer>) invoiceData.get("bbox");
                Double confidence = (Double) invoiceData.getOrDefault("confidence", 0.9);
                String merchantName = (String) invoiceData.getOrDefault("merchantName", null);
                
                // 裁切发票
                String filename = String.format("invoice_%d_%d.%s", page, idx, outputFormat);
                String outputPath = croppedStorageLocation.resolve(filename).toString();
                
                BufferedImage cropped = imageCropService.cropInvoice(image, bbox, padding, outputPath);
                log.debug("裁切完成: 发票索引={}, 文件名={}, 裁切后尺寸={}x{}", 
                    idx, filename, cropped.getWidth(), cropped.getHeight());
                
                // 创建发票信息
                InvoiceInfo invoiceInfo = new InvoiceInfo();
                invoiceInfo.setIndex(idx);
                invoiceInfo.setPage(page);
                invoiceInfo.setBbox(bbox);
                invoiceInfo.setConfidence(confidence);
                invoiceInfo.setMerchantName(merchantName);
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
     * 保存临时图片（确保尺寸一致）
     */
    private String saveTempImage(BufferedImage image, String taskId, int page) throws IOException {
        String filename = String.format("%s_temp_%d.jpg", taskId, page);
        Path targetLocation = tempStorageLocation.resolve(filename);
        
        // 记录图片尺寸，确保用于API的图片和用于裁切的图片尺寸一致
        log.debug("保存临时图片: {}, 尺寸: {}x{}", filename, image.getWidth(), image.getHeight());
        
        // 使用高质量保存，避免压缩导致尺寸变化
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(1.0f); // 最高质量
        }
        
        try (FileImageOutputStream output = new FileImageOutputStream(targetLocation.toFile())) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        
        // 验证保存后的图片尺寸（读取回来检查）
        BufferedImage savedImage = ImageIO.read(targetLocation.toFile());
        if (savedImage != null) {
            if (savedImage.getWidth() != image.getWidth() || savedImage.getHeight() != image.getHeight()) {
                log.error("保存的图片尺寸不匹配！原始: {}x{}, 保存后: {}x{}", 
                    image.getWidth(), image.getHeight(), 
                    savedImage.getWidth(), savedImage.getHeight());
                log.error("这会导致API返回的坐标与裁切时使用的图片尺寸不一致！");
            } else {
                log.debug("保存的图片尺寸验证通过: {}x{}", savedImage.getWidth(), savedImage.getHeight());
            }
        } else {
            log.warn("无法读取保存的图片进行验证: {}", targetLocation);
        }
        
        log.info("临时图片已保存: {}, 原始BufferedImage尺寸: {}x{}, 用于API和裁切的图片应该是同一个对象", 
            filename, image.getWidth(), image.getHeight());
        
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
    
    /**
     * 归一化边界框坐标
     * 如果API返回的是归一化坐标（0-1000），则转换为实际像素坐标
     * 
     * @param invoices 发票列表
     * @param page 页码
     * @param imageWidth 图片宽度
     * @param imageHeight 图片高度
     */
    private void normalizeBboxCoordinates(List<Map<String, Object>> invoices, int page, 
                                         int imageWidth, int imageHeight) {
        for (Map<String, Object> invoice : invoices) {
            invoice.put("page", page);
            @SuppressWarnings("unchecked")
            List<Integer> bbox = (List<Integer>) invoice.get("bbox");
            if (bbox != null && bbox.size() == 4) {
                int x1 = bbox.get(0);
                int y1 = bbox.get(1);
                int x2 = bbox.get(2);
                int y2 = bbox.get(3);
                int maxCoord = Math.max(Math.max(x1, y1), Math.max(x2, y2));
                
                // 如果坐标范围在0-1005（允许一点点溢出），且图片尺寸大，则识别为归一化坐标
                if (maxCoord <= 1005 && (imageWidth > 1200 || imageHeight > 1200)) {
                    log.info("检测到归一化坐标系统 (0-1000)，原始: {}, 图片尺寸: {}x{}", 
                        bbox, imageWidth, imageHeight);
                    
                    // 修正可能超出1000的坐标
                    double normX1 = Math.max(0, Math.min(1000, x1)) / 1000.0;
                    double normY1 = Math.max(0, Math.min(1000, y1)) / 1000.0;
                    double normX2 = Math.max(0, Math.min(1000, x2)) / 1000.0;
                    double normY2 = Math.max(0, Math.min(1000, y2)) / 1000.0;
                    
                    List<Integer> scaledBbox = Arrays.asList(
                        (int) Math.round(normX1 * imageWidth),
                        (int) Math.round(normY1 * imageHeight),
                        (int) Math.round(normX2 * imageWidth),
                        (int) Math.round(normY2 * imageHeight)
                    );
                    
                    log.info("坐标缩放完成: 原始={} -> 归一化比例=[{},{},{},{}] -> 实际像素={}", 
                        bbox, normX1, normY1, normX2, normY2, scaledBbox);
                    invoice.put("bbox", scaledBbox);
                } else {
                    log.debug("识别为像素坐标: bbox={}, 图片尺寸: {}x{}", 
                        bbox, imageWidth, imageHeight);
                }
            }
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

