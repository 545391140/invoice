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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
    
    // 并行处理线程池（限制并发数为1，因为API有严格的限流和配额限制）
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    
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
        return recognizeAndCrop(file, cropPadding, outputFormat, null);
    }

    /**
     * 同步识别与裁切发票（支持指定 taskId）
     */
    public InvoiceRecognizeResponse recognizeAndCrop(MultipartFile file, 
                                                    int cropPadding, 
                                                    String outputFormat,
                                                    String existingTaskId) throws Exception {
        long startTime = System.currentTimeMillis();
        String taskId = (existingTaskId != null && !existingTaskId.isEmpty()) 
            ? existingTaskId 
            : UUID.randomUUID().toString();
        
        log.info("开始处理文件: {}, taskId: {}", file.getOriginalFilename(), taskId);
        
        // 如果是新任务，创建一个任务状态记录
        boolean isNewTask = existingTaskId == null || existingTaskId.isEmpty();
        if (isNewTask) {
            TaskStatusResponse taskStatus = new TaskStatusResponse();
            taskStatus.setTaskId(taskId);
            taskStatus.setStatus("PROCESSING");
            taskStatus.setProgress(10);
            taskStatus.setCreatedAt(Instant.now().toString());
            taskStore.put(taskId, taskStatus);
        }

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
                int totalPages = images.size();
                
                // 更新任务状态的总页数
                TaskStatusResponse taskStatus = taskStore.get(taskId);
                if (taskStatus != null) {
                    taskStatus.setTotalPages(totalPages);
                    taskStatus.setStatusMessage("正在初始化，共 " + totalPages + " 页...");
                }

                // 保存PDF转换后的图片
                for (int i = 0; i < images.size(); i++) {
                    byte[] imageBytes = bufferedImageToBytes(images.get(i));
                    saveOriginalImage(imageBytes, taskId, i + 1);
                }
                
                AtomicInteger completedPages = new AtomicInteger(0);
                List<CompletableFuture<List<InvoiceInfo>>> futures = new ArrayList<>();

                // 并行处理每一页
                for (int pageIndex = 0; pageIndex < images.size(); pageIndex++) {
                    final int page = pageIndex + 1;
                    final BufferedImage image = images.get(pageIndex);
                    
                    CompletableFuture<List<InvoiceInfo>> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            log.info("开始处理第 {} 页，任务ID: {}", page, taskId);
                            
                            // 更新当前页码信息
                            TaskStatusResponse currentStatus = taskStore.get(taskId);
                            if (currentStatus != null) {
                                currentStatus.setCurrentPage(page);
                                currentStatus.setStatusMessage("正在识别第 " + page + "/" + totalPages + " 页...");
                            }

                            // 保存临时图片用于API调用
                            String tempImagePath = saveTempImage(image, taskId, page);
                            
                            // 从临时图片文件读取图片，确保与API看到的图片完全一致
                            BufferedImage tempImage = ImageIO.read(new File(tempImagePath));
                            if (tempImage == null) {
                                throw new IOException("无法读取临时图片文件: " + tempImagePath);
                            }
                            
                            // 调用API识别
                            int imageWidth = tempImage.getWidth();
                            int imageHeight = tempImage.getHeight();
                            String apiResponse = apiService.callVolcengineVisionApi(tempImagePath, page);
                            
                            // 解析API响应
                            List<Map<String, Object>> invoices = responseParser.parseApiResponse(apiResponse, page);
                            
                            // 检查并缩放坐标
                            normalizeBboxCoordinates(invoices, page, imageWidth, imageHeight);
                            
                            // 生成图片唯一ID
                            String imageId = String.format("%s_%d", taskId, page);
                            
                            // 裁切发票
                            List<InvoiceInfo> pageInvoices = cropInvoicesFromImage(
                                tempImage, invoices, taskId, imageId, page, cropPadding, outputFormat);
                            
                            // 更新进度
                            int done = completedPages.incrementAndGet();
                            if (currentStatus != null) {
                                // 进度从 10% 到 90%
                                int progress = 10 + (int)((double)done / totalPages * 80);
                                currentStatus.setProgress(progress);
                                currentStatus.setStatusMessage("已完成 " + done + "/" + totalPages + " 页的识别");
                            }
                            
                            return pageInvoices;
                        } catch (Exception e) {
                            log.error("处理第 {} 页失败: {}", page, e.getMessage());
                            return Collections.emptyList();
                        }
                    }, executorService);
                    
                    futures.add(future);
                }
                
                // 等待所有页面处理完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                // 汇总结果
                for (CompletableFuture<List<InvoiceInfo>> future : futures) {
                    allInvoices.addAll(future.get());
                }
            } else {
                // 图片处理
                BufferedImage image = ImageIO.read(file.getInputStream());
                if (image == null) {
                    throw new IllegalArgumentException("无法读取图片文件: " + file.getOriginalFilename());
                }
                
                // 更新任务状态
                TaskStatusResponse taskStatus = taskStore.get(taskId);
                if (taskStatus != null) {
                    taskStatus.setTotalPages(1);
                    taskStatus.setCurrentPage(1);
                    taskStatus.setStatusMessage("正在识别图片内容...");
                }

                images = Collections.singletonList(image);
                byte[] imageBytes = bufferedImageToBytes(image);
                saveOriginalImage(imageBytes, taskId, 1);
                
                // 保存临时图片
                String tempImagePath = saveTempImage(image, taskId, 1);
                
                // 从临时图片文件读取图片
                BufferedImage tempImage = ImageIO.read(new File(tempImagePath));
                if (tempImage == null) {
                    throw new IOException("无法读取临时图片文件: " + tempImagePath);
                }
                
                // 调用API识别
                int imageWidth = tempImage.getWidth();
                int imageHeight = tempImage.getHeight();
                String apiResponse = apiService.callVolcengineVisionApi(tempImagePath, 1);
                
                // 解析API响应
                List<Map<String, Object>> invoices = responseParser.parseApiResponse(apiResponse, 1);
                
                // 进度更新到 50%
                if (taskStatus != null) {
                    taskStatus.setProgress(50);
                    taskStatus.setStatusMessage("已完成内容识别，正在裁切...");
                }

                // 检查并缩放坐标
                normalizeBboxCoordinates(invoices, 1, imageWidth, imageHeight);
                
                // 生成图片唯一ID
                String imageId = String.format("%s_%d", taskId, 1);
                
                // 裁切发票
                allInvoices = cropInvoicesFromImage(tempImage, invoices, taskId, imageId, 1, cropPadding, outputFormat);
            }
            
            // 构建响应
            InvoiceRecognizeResponse response = new InvoiceRecognizeResponse();
            response.setTaskId(taskId);
            response.setTotalInvoices(allInvoices.size());
            response.setInvoices(allInvoices);
            response.setProcessingTime((System.currentTimeMillis() - startTime) / 1000.0);
            
            log.info("处理完成，识别到 {} 张发票，耗时: {} 秒", 
                allInvoices.size(), response.getProcessingTime());
            
            // 更新任务状态为已完成
            TaskStatusResponse taskStatus = taskStore.get(taskId);
            if (taskStatus != null) {
                taskStatus.setStatus("COMPLETED");
                taskStatus.setProgress(100);
                taskStatus.setTotalInvoices(allInvoices.size());
                taskStatus.setInvoices(allInvoices);
                taskStatus.setCompletedAt(Instant.now().toString());
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("处理失败", e);
            // 更新任务状态为失败
            TaskStatusResponse taskStatus = taskStore.get(taskId);
            if (taskStatus != null) {
                taskStatus.setStatus("FAILED");
                taskStatus.setProgress(0);
            }
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
                    tempMultipartFile, cropPadding, outputFormat, taskId);
                
                // 清理临时文件
                try {
                    Files.deleteIfExists(Paths.get(finalTempFilePath));
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
     * 
     * @param image 要裁切的图片
     * @param invoices 发票列表（包含坐标信息）
     * @param taskId 任务ID
     * @param imageId 图片唯一ID（格式：taskId_page）
     * @param page 页码
     * @param padding 边距
     * @param outputFormat 输出格式
     * @return 发票信息列表
     */
    private List<InvoiceInfo> cropInvoicesFromImage(BufferedImage image,
                                                   List<Map<String, Object>> invoices,
                                                   String taskId,
                                                   String imageId,
                                                   int page,
                                                   int padding,
                                                   String outputFormat) throws IOException {
        List<InvoiceInfo> result = new ArrayList<>();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        log.info("开始裁切发票，图片ID: {}, 图片尺寸: {}x{}, 发票数量: {}", 
            imageId, imageWidth, imageHeight, invoices.size());
        
        for (int idx = 0; idx < invoices.size(); idx++) {
            Map<String, Object> invoiceData = invoices.get(idx);
            try {
                @SuppressWarnings("unchecked")
                List<Integer> bbox = (List<Integer>) invoiceData.get("bbox");
                Double confidence = (Double) invoiceData.getOrDefault("confidence", 0.9);
                String merchantName = (String) invoiceData.getOrDefault("merchantName", null);
                
                int invoicePage = (Integer) invoiceData.getOrDefault("page", page);
                log.info("准备裁切发票 {} (页码 {}): bbox={}, 图片尺寸={}x{}, padding={}, 图片ID={}", 
                    idx, invoicePage, bbox, imageWidth, imageHeight, padding, imageId);
                
                // 生成文件名：{图片唯一ID}_invoice_{页码}_{索引}.{格式}
                String filename = String.format("%s_invoice_%d_%d.%s", imageId, page, idx, outputFormat);
                String outputPath = croppedStorageLocation.resolve(filename).toString();
                
                BufferedImage cropped = imageCropService.cropInvoice(image, bbox, padding, outputPath);
                log.info("裁切完成: 图片ID={}, 发票索引={}, 文件名={}, 裁切后尺寸={}x{}", 
                    imageId, idx, filename, cropped.getWidth(), cropped.getHeight());
                
                // 创建发票信息
                InvoiceInfo invoiceInfo = new InvoiceInfo();
                invoiceInfo.setIndex(idx);
                invoiceInfo.setPage(invoicePage);  // 使用API返回的页码
                invoiceInfo.setBbox(bbox);
                invoiceInfo.setConfidence(confidence);
                invoiceInfo.setMerchantName(merchantName);
                invoiceInfo.setFilename(filename);  // 使用实际生成的文件名
                
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
        
        // 转换为标准RGB格式，避免颜色空间问题
        BufferedImage rgbImage = convertToRGB(image);
        
        // 使用高质量保存，避免压缩导致尺寸变化
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(1.0f); // 最高质量
        }
        
        try (FileImageOutputStream output = new FileImageOutputStream(targetLocation.toFile())) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(rgbImage, null, null), param);
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
     * 确保图片转换为标准RGB格式，避免颜色空间问题，并使用最高质量保存
     */
    private byte[] bufferedImageToBytes(BufferedImage image) throws IOException {
        // 转换为标准RGB格式，避免颜色空间问题
        BufferedImage rgbImage = convertToRGB(image);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // 使用最高质量保存
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(1.0f); // 最高质量
        }
        
        try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgbImage, null, null), param);
        } finally {
            writer.dispose();
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 将BufferedImage转换为标准RGB格式
     * 解决"Bogus input colorspace"错误
     */
    private BufferedImage convertToRGB(BufferedImage image) {
        // 如果已经是RGB格式，直接返回
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        
        // 创建标准RGB格式的BufferedImage
        BufferedImage rgbImage = new BufferedImage(
            image.getWidth(), 
            image.getHeight(), 
            BufferedImage.TYPE_INT_RGB
        );
        
        // 将原图绘制到RGB图片上
        java.awt.Graphics2D g = rgbImage.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, 
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        
        return rgbImage;
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
        
        URI uri = filePath.toUri();
        if (uri == null) {
            throw new IOException("无法创建文件URI: " + filePath);
        }
        Resource resource = new UrlResource(uri);
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
        URI uri = filePath.toUri();
        if (uri == null) {
            throw new IOException("无法创建文件URI: " + filePath);
        }
        Resource resource = new UrlResource(uri);
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
                
                // 如果坐标范围在0-1005（允许一点点溢出），且满足以下任一条件，则识别为归一化坐标：
                // 1. 图片尺寸较大（典型的大图归一化场景）
                // 2. 坐标值已经超出了图片的实际像素边界（强有力的归一化证据）
                if (maxCoord <= 1005 && (imageWidth > 1200 || imageHeight > 1200 || x2 > imageWidth || y2 > imageHeight)) {
                    log.info("检测到归一化坐标系统 (0-1000)，触发原因: {}，原始: {}, 图片尺寸: {}x{}", 
                        (x2 > imageWidth || y2 > imageHeight) ? "坐标越界" : "大图识别",
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

