package com.invoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoice.config.VolcengineConfig;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionContentPart;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class VolcengineApiService {
    
    private final ArkService arkService;
    private final String modelName;
    
    @Autowired
    public VolcengineApiService(VolcengineClient volcengineClient, VolcengineConfig config) {
        this.arkService = volcengineClient.getArkService();
        this.modelName = config.getModel().getName();
    }
    
    /**
     * 调用火山引擎视觉模型 API
     * 
     * @param imagePath 图片文件路径
     * @return API 返回的文本内容
     */
    public String callVolcengineVisionApi(String imagePath) throws Exception {
        return callVolcengineVisionApi(imagePath, null);
    }
    
    /**
     * 调用火山引擎视觉模型 API（使用自定义 prompt）
     * 
     * @param imagePath 图片文件路径
     * @param customPrompt 自定义提示词（可选）
     * @return API 返回的文本内容
     */
    public String callVolcengineVisionApi(String imagePath, String customPrompt) throws Exception {
        try {
            // 准备图片 - 转换为 base64
            byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
            String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
            String imageDataUrl = "data:image/jpeg;base64," + imageBase64;
            
            // 构建 Prompt
            String prompt = customPrompt != null ? customPrompt : 
                "请识别图片中所有发票的位置。\n" +
                "对于每张发票，请返回以下 JSON 格式的信息：\n" +
                "{\n" +
                "  \"invoices\": [\n" +
                "    {\n" +
                "      \"bbox\": [x1, y1, x2, y2],  // 边界框坐标，单位为像素\n" +
                "      \"confidence\": 0.95,         // 置信度 0-1\n" +
                "      \"page\": 1                   // 页码\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "其中 bbox 格式为 [左上角x, 左上角y, 右下角x, 右下角y]";
            
            // 构建消息内容
            List<ChatCompletionContentPart> multiParts = new ArrayList<>();
            
            // 添加图片内容
            multiParts.add(ChatCompletionContentPart.builder()
                    .type("image_url")
                    .imageUrl(new ChatCompletionContentPart.ChatCompletionContentPartImageURL(imageDataUrl))
                    .build());
            
            // 添加文本内容
            multiParts.add(ChatCompletionContentPart.builder()
                    .type("text")
                    .text(prompt)
                    .build());
            
            // 构建消息列表
            List<ChatMessage> messages = new ArrayList<>();
            ChatMessage userMessage = ChatMessage.builder()
                    .role(ChatMessageRole.USER)
                    .multiContent(multiParts)
                    .build();
            messages.add(userMessage);
            
            // 构建请求
            ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                    .model(modelName)
                    .messages(messages)
                    .build();
            
            // 调用 API
            log.info("调用火山引擎 API，模型: {}, 图片: {}", modelName, imagePath);
            var response = arkService.createChatCompletion(chatCompletionRequest);
            
            // 解析响应
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                Object contentObj = response.getChoices().get(0).getMessage().getContent();
                String content = contentObj != null ? contentObj.toString() : "";
                log.info("API 调用成功，返回内容长度: {}", content.length());
                return content;
            } else {
                throw new Exception("API 返回格式异常：未找到 choices");
            }
            
        } catch (Exception e) {
            log.error("API 调用失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 使用图片字节数组调用 API
     */
    public String callVolcengineVisionApi(byte[] imageBytes, int page, String customPrompt) throws Exception {
        try {
            String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
            String imageDataUrl = "data:image/jpeg;base64," + imageBase64;
            
            String prompt = customPrompt != null ? customPrompt : 
                "请识别图片中所有发票的位置。\n" +
                "对于每张发票，请返回以下 JSON 格式的信息：\n" +
                "{\n" +
                "  \"invoices\": [\n" +
                "    {\n" +
                "      \"bbox\": [x1, y1, x2, y2],\n" +
                "      \"confidence\": 0.95,\n" +
                "      \"page\": " + page + "\n" +
                "    }\n" +
                "  ]\n" +
                "}";
            
            List<ChatCompletionContentPart> multiParts = new ArrayList<>();
            multiParts.add(ChatCompletionContentPart.builder()
                    .type("image_url")
                    .imageUrl(new ChatCompletionContentPart.ChatCompletionContentPartImageURL(imageDataUrl))
                    .build());
            multiParts.add(ChatCompletionContentPart.builder()
                    .type("text")
                    .text(prompt)
                    .build());
            
            List<ChatMessage> messages = new ArrayList<>();
            ChatMessage userMessage = ChatMessage.builder()
                    .role(ChatMessageRole.USER)
                    .multiContent(multiParts)
                    .build();
            messages.add(userMessage);
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(modelName)
                    .messages(messages)
                    .build();
            
            var response = arkService.createChatCompletion(request);
            
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                Object contentObj = response.getChoices().get(0).getMessage().getContent();
                return contentObj != null ? contentObj.toString() : "";
            } else {
                throw new Exception("API 返回格式异常");
            }
            
        } catch (Exception e) {
            log.error("API 调用失败: {}", e.getMessage(), e);
            throw e;
        }
    }
}


