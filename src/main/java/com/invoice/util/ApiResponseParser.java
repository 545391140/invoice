package com.invoice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class ApiResponseParser {
    
    private final ObjectMapper objectMapper;
    private static final java.util.regex.Pattern JSON_PATTERN = 
        java.util.regex.Pattern.compile("\\{.*\\}", java.util.regex.Pattern.DOTALL);
    private static final java.util.regex.Pattern BBOX_PATTERN = 
        java.util.regex.Pattern.compile("\\[([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+)\\]");
    
    // 匹配格式：商家名称 发票...<bbox>x1 y1 x2 y2</bbox>
    private static final java.util.regex.Pattern INVOICE_NAME_BBOX_PATTERN = 
        java.util.regex.Pattern.compile("(.+?)\\s*发票.*?<bbox>\\s*([\\d.]+)[,\\s]+([\\d.]+)[,\\s]+([\\d.]+)[,\\s]+([\\d.]+)\\s*</bbox>", java.util.regex.Pattern.CASE_INSENSITIVE);
    
    // 匹配通用的 <bbox>x1 y1 x2 y2</bbox> 格式
    private static final java.util.regex.Pattern GENERIC_BBOX_TAG_PATTERN = 
        java.util.regex.Pattern.compile("<bbox>\\s*([\\d.]+)[,\\s]+([\\d.]+)[,\\s]+([\\d.]+)[,\\s]+([\\d.]+)\\s*</bbox>", java.util.regex.Pattern.CASE_INSENSITIVE);
    
    public ApiResponseParser() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 解析 API 返回的文本，提取发票定位信息
     * 
     * @param apiResponse API返回的文本内容
     * @param pageNumber 页码（从1开始），用于关联坐标到正确的页面
     * @return 发票列表，每个发票包含bbox、confidence、page等信息
     */
    public List<Map<String, Object>> parseApiResponse(String apiResponse, int pageNumber) {
        List<Map<String, Object>> invoices = new ArrayList<>();
        
        if (apiResponse == null || apiResponse.trim().isEmpty()) {
            log.warn("API 返回内容为空，页码: {}", pageNumber);
            return invoices;
        }
        
        log.debug("解析API响应，页码: {}", pageNumber);
        
        // 尝试直接解析 JSON
        try {
            java.util.regex.Matcher jsonMatcher = JSON_PATTERN.matcher(apiResponse);
            if (jsonMatcher.find()) {
                String jsonStr = jsonMatcher.group();
                JsonNode data = objectMapper.readTree(jsonStr);
                
                if (data.has("invoices")) {
                    JsonNode invoicesNode = data.get("invoices");
                    if (invoicesNode.isArray()) {
                        for (JsonNode invoice : invoicesNode) {
                            Map<String, Object> invoiceMap = new HashMap<>();
                            
                            if (invoice.has("bbox") && invoice.get("bbox").isArray()) {
                                double[] raw = new double[4];
                                int i = 0;
                                for (JsonNode coord : invoice.get("bbox")) {
                                    if (i < 4) raw[i++] = coord.asDouble();
                                }
                                List<Integer> bbox = processRawCoordinates(raw[0], raw[1], raw[2], raw[3], apiResponse);
                                log.debug("解析到的归一化bbox坐标: {}", bbox);
                                invoiceMap.put("bbox", bbox);
                            }
                            
                            if (invoice.has("confidence")) {
                                invoiceMap.put("confidence", invoice.get("confidence").asDouble());
                            } else {
                                invoiceMap.put("confidence", 0.9);
                            }
                            
                            // 优先使用API返回的页码，如果没有则使用传入的页码
                            if (invoice.has("page")) {
                                int apiPage = invoice.get("page").asInt();
                                if (apiPage <= 1 && pageNumber > 1) {
                                    invoiceMap.put("page", pageNumber);
                                } else {
                                    invoiceMap.put("page", apiPage);
                                }
                            } else {
                                invoiceMap.put("page", pageNumber);
                            }
                            
                            if (invoice.has("merchantName")) {
                                invoiceMap.put("merchantName", invoice.get("merchantName").asText());
                            }
                            
                            invoices.add(invoiceMap);
                        }
                    }
                }
                
                if (!invoices.isEmpty()) {
                    log.info("成功解析到 {} 张发票 (JSON格式)", invoices.size());
                    return invoices;
                }
            }
        } catch (Exception e) {
            log.warn("JSON 解析失败，尝试正则提取: {}", e.getMessage());
        }
        
        // 如果直接解析失败，尝试正则提取
        if (invoices.isEmpty()) {
            // 1. 尝试匹配：商家名称 发票...<bbox>x1 y1 x2 y2</bbox>
            java.util.regex.Matcher nameBboxMatcher = INVOICE_NAME_BBOX_PATTERN.matcher(apiResponse);
            int index = 0;
            while (nameBboxMatcher.find()) {
                String merchantName = nameBboxMatcher.group(1).trim();
                double x1_r = Double.parseDouble(nameBboxMatcher.group(2));
                double y1_r = Double.parseDouble(nameBboxMatcher.group(3));
                double x2_r = Double.parseDouble(nameBboxMatcher.group(4));
                double y2_r = Double.parseDouble(nameBboxMatcher.group(5));
                
                List<Integer> bbox = processRawCoordinates(x1_r, y1_r, x2_r, y2_r, apiResponse);
                
                Map<String, Object> invoice = new HashMap<>();
                invoice.put("bbox", bbox);
                invoice.put("confidence", 0.9);
                invoice.put("page", pageNumber);
                invoice.put("index", index++);
                invoice.put("merchantName", merchantName);
                invoices.add(invoice);
                log.debug("解析到发票：商家={}, bbox={}, 页码={}", merchantName, bbox, pageNumber);
            }
            
            // 2. 如果没匹配到，尝试通用的 <bbox> 标签匹配
            if (invoices.isEmpty()) {
                java.util.regex.Matcher genericMatcher = GENERIC_BBOX_TAG_PATTERN.matcher(apiResponse);
                while (genericMatcher.find()) {
                    double x1_r = Double.parseDouble(genericMatcher.group(1));
                    double y1_r = Double.parseDouble(genericMatcher.group(2));
                    double x2_r = Double.parseDouble(genericMatcher.group(3));
                    double y2_r = Double.parseDouble(genericMatcher.group(4));
                    
                    List<Integer> bbox = processRawCoordinates(x1_r, y1_r, x2_r, y2_r, apiResponse);
                    
                    Map<String, Object> invoice = new HashMap<>();
                    invoice.put("bbox", bbox);
                    invoice.put("confidence", 0.9);
                    invoice.put("page", pageNumber);
                    invoice.put("index", index++);
                    invoices.add(invoice);
                    log.debug("解析到通用标签发票：bbox={}, 页码={}", bbox, pageNumber);
                }
            }
            
            // 3. 兜底尝试匹配旧的JSON数组格式 [x1, y1, x2, y2]
            if (invoices.isEmpty()) {
                java.util.regex.Matcher bboxMatcher = BBOX_PATTERN.matcher(apiResponse);
                while (bboxMatcher.find()) {
                    double x1_r = Double.parseDouble(bboxMatcher.group(1));
                    double y1_r = Double.parseDouble(bboxMatcher.group(2));
                    double x2_r = Double.parseDouble(bboxMatcher.group(3));
                    double y2_r = Double.parseDouble(bboxMatcher.group(4));
                    
                    List<Integer> bbox = processRawCoordinates(x1_r, y1_r, x2_r, y2_r, apiResponse);
                    
                    Map<String, Object> invoice = new HashMap<>();
                    invoice.put("bbox", bbox);
                    invoice.put("confidence", 0.9);
                    invoice.put("page", pageNumber);
                    invoice.put("index", index++);
                    invoices.add(invoice);
                }
            }
            
            if (!invoices.isEmpty()) {
                log.info("通过正则表达式提取到 {} 张发票", invoices.size());
            }
        }
        
        return invoices;
    }

    private List<Integer> processRawCoordinates(double x1_raw, double y1_raw, double x2_raw, double y2_raw, String apiResponse) {
        // 判定坐标类型：
        // 1. 如果所有坐标都在 0-1 之间（归一化比例坐标），保持为归一化坐标，转换为 0-1000 范围以便后续处理
        // 2. 如果坐标在 0-1000 范围，可能是归一化的像素坐标（相对于1000x1000的图片）
        // 3. 如果坐标很大，可能是实际像素坐标
        
        boolean allUnderOne = x1_raw <= 1.001 && y1_raw <= 1.001 && x2_raw <= 1.001 && y2_raw <= 1.001;
        boolean hasDecimalPoint = apiResponse.contains(".");
        
        // 如果坐标都在 0-1 之间，且是小数，说明是归一化比例坐标
        // 转换为 0-1000 范围的归一化坐标，以便 normalizeBboxCoordinates 正确处理
        if (allUnderOne && hasDecimalPoint && x1_raw > 0 && y1_raw > 0) {
            // 归一化比例坐标 (0-1) -> 归一化像素坐标 (0-1000)
            return Arrays.asList(
                (int) Math.round(x1_raw * 1000.0),
                (int) Math.round(y1_raw * 1000.0),
                (int) Math.round(x2_raw * 1000.0),
                (int) Math.round(y2_raw * 1000.0)
            );
        } else {
            // 直接使用原始坐标（可能是 0-1000 范围的归一化坐标，也可能是实际像素坐标）
            // 后续由 normalizeBboxCoordinates 根据图片实际尺寸判断并转换
            return Arrays.asList(
                (int) Math.round(x1_raw), 
                (int) Math.round(y1_raw), 
                (int) Math.round(x2_raw), 
                (int) Math.round(y2_raw)
            );
        }
    }
}
