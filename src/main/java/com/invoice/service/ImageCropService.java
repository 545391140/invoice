package com.invoice.service;

import com.invoice.util.BboxValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;

@Slf4j
@Service
public class ImageCropService {
    
    @Value("${image.crop.padding:10}")
    private int defaultPadding;
    
    /**
     * 使用 BufferedImage 裁切发票
     */
    public BufferedImage cropInvoice(BufferedImage image, List<Integer> bbox, 
                                    String outputPath) throws IOException {
        return cropInvoice(image, bbox, defaultPadding, outputPath);
    }
    
    /**
     * 使用 BufferedImage 裁切发票（带边距）
     */
    public BufferedImage cropInvoice(BufferedImage image, List<Integer> bbox, 
                                    int padding, String outputPath) throws IOException {
        if (image == null) {
            throw new IllegalArgumentException("图片不能为空");
        }
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        // 验证坐标
        BboxValidator.BboxValidationResult validation = BboxValidator.validateBbox(bbox, width, height);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("无效的边界框坐标: " + bbox + 
                (validation.getMessage() != null ? ", " + validation.getMessage() : ""));
        }
        
        List<Integer> correctedBbox = validation.getCorrectedBbox();
        int x1 = correctedBbox.get(0);
        int y1 = correctedBbox.get(1);
        int x2 = correctedBbox.get(2);
        int y2 = correctedBbox.get(3);
        
        // 添加边距
        x1 = Math.max(0, x1 - padding);
        y1 = Math.max(0, y1 - padding);
        x2 = Math.min(width, x2 + padding);
        y2 = Math.min(height, y2 + padding);
        
        // 执行裁切
        BufferedImage cropped = image.getSubimage(x1, y1, x2 - x1, y2 - y1);
        
        // 保存（如果指定了输出路径）
        if (outputPath != null) {
            Path outputDir = Paths.get(outputPath).getParent();
            if (outputDir != null) {
                Files.createDirectories(outputDir);
            }
            ImageIO.write(cropped, "jpg", new File(outputPath));
            log.info("已保存裁切后的图片: {}", outputPath);
        }
        
        return cropped;
    }
    
    /**
     * 从文件路径读取图片并裁切
     */
    public BufferedImage cropInvoice(String imagePath, List<Integer> bbox, 
                                    int padding, String outputPath) throws IOException {
        BufferedImage image = ImageIO.read(new File(imagePath));
        if (image == null) {
            throw new IllegalArgumentException("无法读取图片: " + imagePath);
        }
        return cropInvoice(image, bbox, padding, outputPath);
    }
}

