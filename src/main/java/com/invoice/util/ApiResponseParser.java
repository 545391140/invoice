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
    
    /**
     * 合并重叠或相邻的边界框
     * 当多个 bbox 重叠或相邻时，合并成一个大的 bbox
     */
    private List<Map<String, Object>> mergeOverlappingBboxes(List<Map<String, Object>> invoices) {
        if (invoices.size() <= 1) {
            return invoices;
        }
        
        List<Map<String, Object>> merged = new ArrayList<>();
        List<boolean[]> mergedFlags = new ArrayList<>();
        
        for (int i = 0; i < invoices.size(); i++) {
            if (mergedFlags.size() > i && mergedFlags.get(i)[0]) {
                continue; // 已经被合并
            }
            
            @SuppressWarnings("unchecked")
            List<Integer> bbox1 = (List<Integer>) invoices.get(i).get("bbox");
            if (bbox1 == null || bbox1.size() != 4) {
                merged.add(invoices.get(i));
                continue;
            }
            
            int x1 = bbox1.get(0);
            int y1 = bbox1.get(1);
            int x2 = bbox1.get(2);
            int y2 = bbox1.get(3);
            
            // 尝试与其他 bbox 合并
            for (int j = i + 1; j < invoices.size(); j++) {
                if (mergedFlags.size() > j && mergedFlags.get(j)[0]) {
                    continue;
                }
                
                @SuppressWarnings("unchecked")
                List<Integer> bbox2 = (List<Integer>) invoices.get(j).get("bbox");
                if (bbox2 == null || bbox2.size() != 4) {
                    continue;
                }
                
                int bx1 = bbox2.get(0);
                int by1 = bbox2.get(1);
                int bx2 = bbox2.get(2);
                int by2 = bbox2.get(3);
                
                // 检查是否重叠或相邻（允许一定的间隙，比如50像素）
                int gapThreshold = 50;
                boolean shouldMerge = false;
                
                // 检查重叠
                boolean overlaps = !(x2 < bx1 || bx2 < x1 || y2 < by1 || by2 < y1);
                
                // 检查是否在同一列（垂直相邻，可能是收据的上下部分）
                boolean sameColumn = Math.abs((x1 + x2) / 2 - (bx1 + bx2) / 2) < gapThreshold;
                boolean verticalAdjacent = sameColumn && 
                    (Math.abs(y2 - by1) < gapThreshold || Math.abs(by2 - y1) < gapThreshold);
                
                // 检查是否在同一行（水平相邻，可能是收据的左右部分）
                boolean sameRow = Math.abs((y1 + y2) / 2 - (by1 + by2) / 2) < gapThreshold;
                boolean horizontalAdjacent = sameRow && 
                    (Math.abs(x2 - bx1) < gapThreshold || Math.abs(bx2 - x1) < gapThreshold);
                
                // 如果重叠或相邻，则合并
                if (overlaps || verticalAdjacent || horizontalAdjacent) {
                    shouldMerge = true;
                    log.debug("检测到可合并的 bbox: [{}] 和 [{}], 重叠={}, 垂直相邻={}, 水平相邻={}", 
                        bbox1, bbox2, overlaps, verticalAdjacent, horizontalAdjacent);
                }
                
                if (shouldMerge) {
                    // 合并 bbox
                    x1 = Math.min(x1, bx1);
                    y1 = Math.min(y1, by1);
                    x2 = Math.max(x2, bx2);
                    y2 = Math.max(y2, by2);
                    
                    // 标记已合并
                    while (mergedFlags.size() <= j) {
                        mergedFlags.add(new boolean[]{false});
                    }
                    mergedFlags.get(j)[0] = true;
                    
                    log.debug("合并 bbox: [{}] 和 [{}] -> [{},{},{},{}]", 
                        bbox1, bbox2, x1, y1, x2, y2);
                }
            }
            
            // 创建合并后的发票信息
            Map<String, Object> mergedInvoice = new HashMap<>(invoices.get(i));
            mergedInvoice.put("bbox", Arrays.asList(x1, y1, x2, y2));
            merged.add(mergedInvoice);
        }
        
        return merged;
    }
}


