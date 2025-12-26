package com.invoice.service;

import com.invoice.util.BboxValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.IIOImage;
import javax.imageio.stream.FileImageOutputStream;

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
        
        log.debug("原始坐标: bbox={}, 图片尺寸: {}x{}", bbox, width, height);
        log.debug("修正后坐标: [x1={}, y1={}, x2={}, y2={}]", x1, y1, x2, y2);
        
        // 添加边距
        x1 = Math.max(0, x1 - padding);
        y1 = Math.max(0, y1 - padding);
        x2 = Math.min(width, x2 + padding);
        y2 = Math.min(height, y2 + padding);
        
        log.info("添加边距后坐标: [x1={}, y1={}, x2={}, y2={}], 边距={}, 裁切尺寸: {}x{}", 
            x1, y1, x2, y2, padding, x2 - x1, y2 - y1);
        
        // 验证裁切区域有效性
        if (x2 <= x1 || y2 <= y1) {
            throw new IllegalArgumentException(
                String.format("无效的裁切区域: x2(%d) <= x1(%d) 或 y2(%d) <= y1(%d)", x2, x1, y2, y1));
        }
        
        if (x2 - x1 < 10 || y2 - y1 < 10) {
            log.warn("裁切区域过小: {}x{}, 可能不准确", x2 - x1, y2 - y1);
        }
        
        // 执行裁切（直接裁切，不进行缩放）
        int cropWidth = x2 - x1;
        int cropHeight = y2 - y1;
        
        // 验证裁切区域是否超出图片边界
        if (x1 + cropWidth > width || y1 + cropHeight > height) {
            log.error("裁切区域超出图片边界！图片尺寸: {}x{}, 裁切区域: [{},{},{},{}]", 
                width, height, x1, y1, x2, y2);
            // 修正到图片范围内
            x2 = Math.min(x2, width);
            y2 = Math.min(y2, height);
            cropWidth = x2 - x1;
            cropHeight = y2 - y1;
            log.warn("已修正裁切区域: [{},{},{},{}], 修正后尺寸: {}x{}", 
                x1, y1, x2, y2, cropWidth, cropHeight);
        }
        
        BufferedImage cropped = image.getSubimage(x1, y1, cropWidth, cropHeight);
        int croppedWidth = cropped.getWidth();
        int croppedHeight = cropped.getHeight();
        
        // 验证裁切后的尺寸是否与预期一致
        if (croppedWidth != cropWidth || croppedHeight != cropHeight) {
            log.error("裁切尺寸不匹配！预期: {}x{}, 实际: {}x{}", 
                cropWidth, cropHeight, croppedWidth, croppedHeight);
        }
        
        log.info("裁切完成: 原始尺寸={}x{}, 裁切区域=[{},{},{},{}], 结果尺寸={}x{} (无缩放)", 
            width, height, x1, y1, x2, y2, croppedWidth, croppedHeight);
        log.debug("裁切区域占比: 宽度={}% ({}/{})，高度={}% ({}/{})", 
            String.format("%.1f", (croppedWidth * 100.0 / width)), croppedWidth, width,
            String.format("%.1f", (croppedHeight * 100.0 / height)), croppedHeight, height);
        
        // 保存（如果指定了输出路径）
        if (outputPath != null) {
            Path outputDir = Paths.get(outputPath).getParent();
            if (outputDir != null) {
                Files.createDirectories(outputDir);
            }
            
            // 转换为标准RGB格式，避免颜色空间问题
            BufferedImage rgbCropped = convertToRGB(cropped);
            
            // 使用最高质量保存，避免压缩导致质量损失
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(1.0f); // 最高质量
            }
            
            try (FileImageOutputStream output = new FileImageOutputStream(new File(outputPath))) {
                writer.setOutput(output);
                writer.write(null, new IIOImage(rgbCropped, null, null), param);
            } finally {
                writer.dispose();
            }
            
            // 验证保存后的图片尺寸
            BufferedImage savedImage = ImageIO.read(new File(outputPath));
            if (savedImage != null) {
                if (savedImage.getWidth() != croppedWidth || savedImage.getHeight() != croppedHeight) {
                    log.error("保存后的图片尺寸不匹配！裁切后: {}x{}, 保存后: {}x{}", 
                        croppedWidth, croppedHeight, savedImage.getWidth(), savedImage.getHeight());
                } else {
                    log.debug("保存后的图片尺寸验证通过: {}x{} (无缩放)", 
                        savedImage.getWidth(), savedImage.getHeight());
                }
            }
            
            log.info("已保存裁切后的图片: {}, 尺寸: {}x{} (无缩放)", 
                outputPath, croppedWidth, croppedHeight);
        }
        
        return cropped;
    }
    
    /**
     * 将BufferedImage转换为标准RGB格式
     * 解决"Bogus input colorspace"错误
     */
    private BufferedImage convertToRGB(BufferedImage image) {
        // 如果已经是RGB格式，直接返回
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        
        // 创建标准RGB格式的BufferedImage
        BufferedImage rgbImage = new BufferedImage(
            image.getWidth(), 
            image.getHeight(), 
            BufferedImage.TYPE_INT_RGB
        );
        
        // 将原图绘制到RGB图片上
        Graphics2D g = rgbImage.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        
        return rgbImage;
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


