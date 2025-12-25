package com.invoice.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PdfProcessor {
    
    @Value("${image.pdf.dpi:300}")
    private int dpi;
    
    /**
     * 将 PDF 转换为图片数组
     */
    public List<byte[]> pdfToImages(String pdfPath) throws IOException {
        List<byte[]> images = new ArrayList<>();
        
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            
            log.info("开始转换 PDF，共 {} 页", document.getNumberOfPages());
            
            // 遍历每一页
            for (int pageNum = 0; pageNum < document.getNumberOfPages(); pageNum++) {
                // 渲染为图片，DPI 设置为 300
                BufferedImage image = pdfRenderer.renderImageWithDPI(
                    pageNum, dpi, ImageType.RGB);
                
                // 转换为字节流
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", baos);
                images.add(baos.toByteArray());
                
                log.info("已转换第 {} 页，图片大小: {} bytes", pageNum + 1, baos.size());
            }
        }
        
        log.info("PDF 转换完成，共生成 {} 张图片", images.size());
        return images;
    }
    
    /**
     * 将 PDF 转换为 BufferedImage 列表
     */
    public List<BufferedImage> pdfToBufferedImages(String pdfPath) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            
            for (int pageNum = 0; pageNum < document.getNumberOfPages(); pageNum++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(
                    pageNum, dpi, ImageType.RGB);
                images.add(image);
            }
        }
        
        return images;
    }
    
    /**
     * 将 PDF 转换为 BufferedImage 列表（从字节数组）
     */
    public List<BufferedImage> pdfToBufferedImages(byte[] pdfBytes) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            
            for (int pageNum = 0; pageNum < document.getNumberOfPages(); pageNum++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(
                    pageNum, dpi, ImageType.RGB);
                images.add(image);
            }
        }
        
        return images;
    }
}

