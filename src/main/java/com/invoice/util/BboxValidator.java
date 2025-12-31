package com.invoice.util;

import java.util.List;

public class BboxValidator {
    
    /**
     * 验证边界框坐标是否有效
     */
    public static BboxValidationResult validateBbox(List<Integer> bbox, 
                                                    int imageWidth, 
                                                    int imageHeight) {
        if (bbox == null || bbox.size() != 4) {
            return new BboxValidationResult(false, null, "边界框坐标格式错误，应为4个元素");
        }
        
        int x1 = bbox.get(0);
        int y1 = bbox.get(1);
        int x2 = bbox.get(2);
        int y2 = bbox.get(3);
        
        // 检查坐标顺序
        if (x2 <= x1 || y2 <= y1) {
            return new BboxValidationResult(false, null, 
                String.format("坐标顺序错误: x2(%d) <= x1(%d) 或 y2(%d) <= y1(%d)", x2, x1, y2, y1));
        }
        
        // 检查坐标范围
        if (x1 < 0 || y1 < 0 || x2 > imageWidth || y2 > imageHeight) {
            // 修正到图片范围内
            int correctedX1 = Math.max(0, x1);
            int correctedY1 = Math.max(0, y1);
            int correctedX2 = Math.min(imageWidth, x2);
            int correctedY2 = Math.min(imageHeight, y2);
            
            // 再次检查顺序
            if (correctedX2 <= correctedX1 || correctedY2 <= correctedY1) {
                return new BboxValidationResult(false, null, 
                    String.format("修正后的坐标无效: [x1=%d, y1=%d, x2=%d, y2=%d]", 
                        correctedX1, correctedY1, correctedX2, correctedY2));
            }
            
            return new BboxValidationResult(true, 
                List.of(correctedX1, correctedY1, correctedX2, correctedY2),
                String.format("坐标已修正到图片范围内: [%d,%d,%d,%d] -> [%d,%d,%d,%d]", 
                    x1, y1, x2, y2, correctedX1, correctedY1, correctedX2, correctedY2));
        }
        
        return new BboxValidationResult(true, bbox, null);
    }
    
    public static class BboxValidationResult {
        private final boolean valid;
        private final List<Integer> correctedBbox;
        private final String message;
        
        public BboxValidationResult(boolean valid, List<Integer> correctedBbox, String message) {
            this.valid = valid;
            this.correctedBbox = correctedBbox;
            this.message = message;
        }
        
        public boolean isValid() { 
            return valid; 
        }
        
        public List<Integer> getCorrectedBbox() { 
            return correctedBbox; 
        }
        
        public String getMessage() {
            return message;
        }
    }
}

















