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
        java.util.regex.Pattern.compile("\\[(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)\\]");
    
    public ApiResponseParser() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 解析 API 返回的文本，提取发票定位信息
     */
    public List<Map<String, Object>> parseApiResponse(String apiResponse) {
        List<Map<String, Object>> invoices = new ArrayList<>();
        
        if (apiResponse == null || apiResponse.trim().isEmpty()) {
            log.warn("API 返回内容为空");
            return invoices;
        }
        
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
                                List<Integer> bbox = new ArrayList<>();
                                for (JsonNode coord : invoice.get("bbox")) {
                                    bbox.add(coord.asInt());
                                }
                                invoiceMap.put("bbox", bbox);
                            }
                            
                            if (invoice.has("confidence")) {
                                invoiceMap.put("confidence", invoice.get("confidence").asDouble());
                            } else {
                                invoiceMap.put("confidence", 0.9);
                            }
                            
                            if (invoice.has("page")) {
                                invoiceMap.put("page", invoice.get("page").asInt());
                            } else {
                                invoiceMap.put("page", 1);
                            }
                            
                            invoices.add(invoiceMap);
                        }
                    }
                }
                
                if (!invoices.isEmpty()) {
                    log.info("成功解析到 {} 张发票", invoices.size());
                    return invoices;
                }
            }
        } catch (Exception e) {
            log.warn("JSON 解析失败，尝试正则提取: {}", e.getMessage());
        }
        
        // 如果直接解析失败，尝试正则提取
        if (invoices.isEmpty()) {
            java.util.regex.Matcher bboxMatcher = BBOX_PATTERN.matcher(apiResponse);
            int index = 0;
            while (bboxMatcher.find()) {
                int x1 = Integer.parseInt(bboxMatcher.group(1));
                int y1 = Integer.parseInt(bboxMatcher.group(2));
                int x2 = Integer.parseInt(bboxMatcher.group(3));
                int y2 = Integer.parseInt(bboxMatcher.group(4));
                
                Map<String, Object> invoice = new HashMap<>();
                invoice.put("bbox", Arrays.asList(x1, y1, x2, y2));
                invoice.put("confidence", 0.9);  // 默认置信度
                invoice.put("page", 1);  // 默认页码
                invoice.put("index", index++);
                invoices.add(invoice);
            }
            
            if (!invoices.isEmpty()) {
                log.info("通过正则表达式提取到 {} 张发票", invoices.size());
            }
        }
        
        return invoices;
    }
}

