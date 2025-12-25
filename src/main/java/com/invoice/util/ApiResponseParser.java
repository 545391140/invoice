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
    // 匹配格式：商家名称 发票：<bbox>x1 y1 x2 y2</bbox>
    private static final java.util.regex.Pattern INVOICE_NAME_BBOX_PATTERN = 
        java.util.regex.Pattern.compile("(.+?)\\s*发票：\\s*<bbox>\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*</bbox>", java.util.regex.Pattern.CASE_INSENSITIVE);
    
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
                                log.debug("解析到的原始bbox坐标: {}", bbox);
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
                            
                            // 支持商家名称字段
                            if (invoice.has("merchantName")) {
                                invoiceMap.put("merchantName", invoice.get("merchantName").asText());
                            }
                            
                            invoices.add(invoiceMap);
                        }
                    }
                }
                
                if (!invoices.isEmpty()) {
                    log.info("成功解析到 {} 张发票", invoices.size());
                    // 暂时禁用合并逻辑，因为API可能返回多张独立的发票
                    // 只有在明确需要合并的情况下才启用（比如一张发票被分成多个部分）
                    // List<Map<String, Object>> mergedInvoices = mergeOverlappingBboxes(invoices);
                    // if (mergedInvoices.size() < invoices.size()) {
                    //     log.info("合并后剩余 {} 张发票", mergedInvoices.size());
                    // }
                    // return mergedInvoices;
                    return invoices;
                }
            }
        } catch (Exception e) {
            log.warn("JSON 解析失败，尝试正则提取: {}", e.getMessage());
        }
        
        // 如果直接解析失败，尝试正则提取
        if (invoices.isEmpty()) {
            // 首先尝试匹配新格式：商家名称 发票：<bbox>x1 y1 x2 y2</bbox>
            java.util.regex.Matcher nameBboxMatcher = INVOICE_NAME_BBOX_PATTERN.matcher(apiResponse);
            int index = 0;
            while (nameBboxMatcher.find()) {
                String merchantName = nameBboxMatcher.group(1).trim();
                int x1 = Integer.parseInt(nameBboxMatcher.group(2));
                int y1 = Integer.parseInt(nameBboxMatcher.group(3));
                int x2 = Integer.parseInt(nameBboxMatcher.group(4));
                int y2 = Integer.parseInt(nameBboxMatcher.group(5));
                
                Map<String, Object> invoice = new HashMap<>();
                invoice.put("bbox", Arrays.asList(x1, y1, x2, y2));
                invoice.put("confidence", 0.9);  // 默认置信度
                invoice.put("page", 1);  // 默认页码
                invoice.put("index", index++);
                invoice.put("merchantName", merchantName);  // 保存商家名称
                invoices.add(invoice);
                log.debug("解析到发票：商家={}, bbox=[{},{},{},{}]", merchantName, x1, y1, x2, y2);
            }
            
            // 如果新格式没有匹配到，尝试匹配旧的JSON数组格式
            if (invoices.isEmpty()) {
                java.util.regex.Matcher bboxMatcher = BBOX_PATTERN.matcher(apiResponse);
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
            }
            
            if (!invoices.isEmpty()) {
                log.info("通过正则表达式提取到 {} 张发票", invoices.size());
            }
        }
        
        // 暂时禁用合并逻辑，因为API可能返回多张独立的发票
        // if (!invoices.isEmpty()) {
        //     List<Map<String, Object>> mergedInvoices = mergeOverlappingBboxes(invoices);
        //     if (mergedInvoices.size() < invoices.size()) {
        //         log.info("通过正则表达式提取到 {} 张发票，合并后剩余 {} 张", 
        //             invoices.size(), mergedInvoices.size());
        //     }
        //     return mergedInvoices;
        // }
        
        return invoices;
    }
}


