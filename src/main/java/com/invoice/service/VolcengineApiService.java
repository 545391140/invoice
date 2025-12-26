package com.invoice.service;

import com.invoice.config.VolcengineConfig;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionContentPart;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
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
    
    private static final String DEFAULT_PROMPT = 
        "请识别图片中所有发票或收据的位置。\n" +
        "重要：每张发票必须返回一个精确的边界框，紧贴发票的实际内容边界，不包括任何空白区域。\n" +
        "边界框必须：\n" +
        "1. 从发票最顶部的第一行文字开始（如商店名称、标题），不要包含图片上方的空白区域\n" +
        "2. 到发票最底部的最后一行文字结束（如总计、流水号、日期），不要包含图片下方的空白区域\n" +
        "3. 左右边界紧贴发票内容的左右边缘，不要包含图片左右两侧的空白区域\n" +
        "4. 确保边界框足够宽，包含所有文字内容，不要切断左右两边的文字\n" +
        "严格禁止：\n" +
        "- 禁止返回整张图片的坐标 [0, 0, 1000, 1000] 或类似的整图坐标\n" +
        "- 禁止 y2 坐标等于 1000（这表示包含了整张图片的高度）\n" +
        "- 禁止 y1 坐标等于 0（除非发票确实从图片最顶部开始）\n" +
        "- 禁止包含发票周围的空白区域\n" +
        "对于每张发票，请识别发票上的商家名称或店铺名称，并严格按照以下格式返回：\n" +
        "商家名称 发票：<bbox>x1 y1 x2 y2</bbox>\n" +
        "其中 bbox 格式为：左上角x坐标 左上角y坐标 右下角x坐标 右下角y坐标。\n" +
        "重要：坐标必须使用归一化坐标系统，范围为 0 到 1000。\n" +
        "坐标要求：\n" +
        "- x1, y1 必须是发票内容实际开始的左上角坐标（不是图片边缘）\n" +
        "- x2, y2 必须是发票内容实际结束的右下角坐标（不是图片边缘）\n" +
        "- y2 必须小于 1000（除非发票确实延伸到图片底部，但即使如此也要精确到实际内容结束位置）\n" +
        "- y1 必须大于 0（除非发票确实从图片顶部开始）\n" +
        "示例格式（使用归一化坐标0-1000）：\n" +
        "Burger King 发票：<bbox>0 5 276 598</bbox>\n" +
        "NUTFAKTANİSTE CATERING 发票：<bbox>275 5 544 596</bbox>\n" +
        "每张发票一行，如果有多张发票，请分行列出。\n" +
        "警告：如果返回 y2=1000 或 y1=0 且同时 x1=0 和 x2=1000，将被视为错误。";
    
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
        return callVolcengineVisionApi(imagePath, null, 1);
    }
    
    /**
     * 调用火山引擎视觉模型 API（指定页码）
     * 
     * @param imagePath 图片文件路径
     * @param pageNumber 页码（从1开始），用于在prompt中明确标识
     * @return API 返回的文本内容
     */
    public String callVolcengineVisionApi(String imagePath, int pageNumber) throws Exception {
        return callVolcengineVisionApi(imagePath, null, pageNumber);
    }
    
    /**
     * 调用火山引擎视觉模型 API（使用自定义 prompt）
     * 
     * @param imagePath 图片文件路径
     * @param customPrompt 自定义提示词（可选）
     * @param pageNumber 页码（从1开始），用于在prompt中明确标识
     * @return API 返回的文本内容
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
            
            // 构建 Prompt - 包含图片尺寸和页码信息
            String basePrompt = customPrompt != null ? customPrompt : DEFAULT_PROMPT;
            String pageInfo = pageNumber > 1 
                ? String.format("\n\n重要提示：这是第 %d 页图片，实际尺寸为 %dx%d 像素。请确保返回的归一化坐标（0-1000）是基于这个实际尺寸计算的。", 
                    pageNumber, imageWidth, imageHeight)
                : String.format("\n\n重要提示：当前图片的实际尺寸为 %dx%d 像素。请确保返回的归一化坐标（0-1000）是基于这个实际尺寸计算的。",
                    imageWidth, imageHeight);
            String prompt = basePrompt + pageInfo;
            
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
                log.debug("API 返回的原始内容: {}", content);
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
            
            // 构建 Prompt - 包含页码信息
            String basePrompt = customPrompt != null ? customPrompt : DEFAULT_PROMPT;
            String pageInfo = page > 1 
                ? String.format("\n\n重要提示：这是第 %d 页图片。请确保返回的归一化坐标（0-1000）是基于图片实际尺寸计算的。", page)
                : "\n\n重要提示：请确保返回的归一化坐标（0-1000）是基于图片实际尺寸计算的。";
            String prompt = basePrompt + pageInfo;
            
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


