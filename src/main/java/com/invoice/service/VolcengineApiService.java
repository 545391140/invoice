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
        "你是发票识别专家。请识别图片中所有发票或收据的位置，并返回边界框坐标。\n" +
        "【核心原则：宽松优先，绝不切边】\n" +
        "在预测边界框时，必须遵循「宁多勿少」原则。包含稍许背景比切断内容好一万倍。\n" +
        "【右侧边缘 - 特别注意】\n" +
        "右边界（x2）必须完整包含：\n" +
        "- 主要金额数字的最后一位和货币符号\n" +
        "- 侧边的订单号、编号、注释等小字\n" +
        "- 右侧可能存在的竖排文字或装饰线\n" +
        "▶ 建议：在你初步判断的 x2 基础上，再向右加 30-50 个归一化单位作为安全余量。\n" +
        "【顶部边缘 - 特别注意】\n" +
        "顶边（y1）必须完整包含：\n" +
        "- 商店 Logo 或品牌标识的最顶端\n" +
        "- 标题文字的最上方\n" +
        "- 顶部装饰线或横条\n" +
        "▶ 建议：在你初步判断的 y1 基础上，再向上减 20-30 个归一化单位。\n" +
        "【输出格式】\n" +
        "商家名称 发票：<bbox>x1 y1 x2 y2</bbox>\n" +
        "使用归一化坐标 (0-1000)。\n" +
        "严禁输出整图坐标 [0, 0, 1000, 1000]，除非发票真的占满全图。";
    
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
            requestBody.put("temperature", 0.0); // 设置温度为0，减少识别结果的随机性
            
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

    /**
     * AI 自我校验：检查识别结果并进行修正
     */
    public List<Integer> selfVerifyBbox(String originalImagePath, List<Integer> bbox) throws Exception {
        log.info("执行 AI 自我校验，原始 bbox: {}", bbox);
        String verifyPrompt = String.format(
            "请作为校验员复核该发票范围：\n" +
            "当前范围是 %s (归一化坐标 0-1000)。\n" +
            "重点检查：右侧金额是否完整？顶部Logo是否被切？\n" +
            "如果发现切断，请给出一个更宽大的 bbox 确保 100%% 完整。\n" +
            "格式：<bbox>x1 y1 x2 y2</bbox>", bbox.toString());
        
        String response = callVolcengineVisionApi(originalImagePath, verifyPrompt, 1);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<bbox>\\s*([\\d.]+)[,\\s]+([\\d.]+)[,\\s]+([\\d.]+)[,\\s]+([\\d.]+)\\s*</bbox>").matcher(response);
        if (m.find()) {
            return Arrays.asList(
                (int) Math.round(Double.parseDouble(m.group(1))),
                (int) Math.round(Double.parseDouble(m.group(2))),
                (int) Math.round(Double.parseDouble(m.group(3))),
                (int) Math.round(Double.parseDouble(m.group(4)))
            );
        }
        return bbox;
    }
}


