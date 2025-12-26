package com.invoice.model;

import lombok.Data;
import java.util.List;

@Data
public class InvoiceInfo {
    private Integer index;
    private Integer page;
    private List<Integer> bbox;
    private Double confidence;
    private String merchantName;       // 商家名称
    private String imageUrl;           // 裁切后图片预览URL
    private String downloadUrl;        // 裁切后图片下载URL
    private String originalImageUrl;   // 原始图片预览URL
    private String filename;
}



