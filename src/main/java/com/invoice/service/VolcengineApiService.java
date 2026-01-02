package com.invoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoice.config.VolcengineConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VolcengineApiService {
    
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    private static final String DEFAULT_PROMPT = 
        "请识别图片中所有发票或收据的位置。\n" +
        "重要提示：视觉模型在预测边界框时必须极其精确，特别要注意包含发票的四个边缘，确保内容 100% 完整。\n" +
        "边界框必须：\n" +
        "1. 【顶部关键】：必须从发票最顶端的 Logo、商店名称或任何标题文字开始。宁愿在上方多包含 5% 的空白，也绝对不能切断顶部的文字。\n" +
        "2. 【右侧关键】：必须完整包含发票右侧的所有文字、数字和金额边缘。预测 x2（右边界）时要稍微向右偏移，确保不切断任何最右边的字符。\n" +
        "3. 【底部关键】：必须延伸到发票最底部的最后一行文字（如总计、日期、流水号、广告语或 CamScanner 扫描水印上方）。\n" +
        "4. 【左侧关键】：紧贴左侧边缘，包含所有侧边文字。\n" +
        "严格禁止：\n" +
        "- 禁止返回整张图片的坐标 [0, 0, 1000, 1000]\n" +
        "- 如果发票明显只占据图片的一部分，不要返回接近整图的坐标，除非它确实占据了全宽或全高\n" +
        "对于每张发票，请识别其商家名称，并严格按此格式返回：\n" +
        "商家名称 发票：<bbox>x1 y1 x2 y2</bbox>\n" +
        "使用归一化坐标 (0-1000)。\n" +
        "警告：如果你的 x2 或 y1 坐标设置得太紧，导致切掉了发票边缘的任何文字或 Logo，将被视为严重错误。";
    
    @Autowired
    public VolcengineApiService(VolcengineConfig config) {
        this.apiKey = config.getArkApiKey();
        this.baseUrl = config.getBaseUrl();
        this.modelName = config.getModel().getName();
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS) // 增加到5分钟，视觉模型处理可能较慢
                .build();
        
        log.info("VolcengineApiService 初始化完成，使用 Base URL: {}", this.baseUrl);
    }
    
    /**
     * 调用视觉模型 API
     */
    public String callVolcengineVisionApi(String imagePath) throws Exception {
        return callVolcengineVisionApi(imagePath, null, 1);
    }
    
    /**
     * 调用视觉模型 API（指定页码）
     */
    public String callVolcengineVisionApi(String imagePath, int pageNumber) throws Exception {
        return callVolcengineVisionApi(imagePath, null, pageNumber);
    }
    
    /**
     * 调用视觉模型 API（使用自定义 prompt）
     */
    public String callVolcengineVisionApi(String imagePath, String customPrompt, int pageNumber) throws Exception {
        try {
            // 读取图片尺寸
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new File(imagePath));
            int imageWidth = img != null ? img.getWidth() : 0;
            int imageHeight = img != null ? img.getHeight() : 0;
            
            // 准备图片 - 转换为 base64
            byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
            String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
            String imageDataUrl = "data:image/jpeg;base64," + imageBase64;
            
            // 构建 Prompt
            String basePrompt = customPrompt != null ? customPrompt : DEFAULT_PROMPT;
            String pageInfo = pageNumber > 1 
                ? String.format("\n\n重要提示：这是第 %d 页图片，实际尺寸为 %dx%d 像素。请确保返回的归一化坐标（0-1000）是基于这个实际尺寸计算的。", 
                    pageNumber, imageWidth, imageHeight)
                : String.format("\n\n重要提示：当前图片的实际尺寸为 %dx%d 像素。请确保返回的归一化坐标（0-1000）是基于这个实际尺寸计算的。",
                    imageWidth, imageHeight);
            String prompt = basePrompt + pageInfo;
            
            // 构建 OpenAI 格式的请求
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", prompt);
            content.add(textPart);
            
            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            Map<String, String> imageUrl = new HashMap<>();
            imageUrl.put("url", imageDataUrl);
            imagePart.put("image_url", imageUrl);
            content.add(imagePart);
            
            userMessage.put("content", content);
            messages.add(userMessage);
            requestBody.put("messages", messages);
            
            String json = objectMapper.writeValueAsString(requestBody);
            
            // 发送请求
            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
            log.info("调用 API: {}, 模型: {}, 图片: {}", url, modelName, imagePath);
            
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "empty body";
                    log.error("API 调用失败: HTTP {}, Body: {}", response.code(), errorBody);
                    throw new IOException("API 错误: " + response.code() + " " + errorBody);
                }
                
                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);
                String contentResult = root.path("choices").get(0).path("message").path("content").asText();
                
                log.info("API 调用成功，返回内容长度: {}", contentResult.length());
                return contentResult;
            }
            
        } catch (Exception e) {
            log.error("API 调用异常: {}", e.getMessage(), e);
            throw e;
        }
    }
}


