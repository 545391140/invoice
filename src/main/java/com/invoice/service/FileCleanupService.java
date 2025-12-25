package com.invoice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class FileCleanupService {
    
    @Value("${app.upload-folder:uploads}")
    private String uploadFolder;
    
    @Value("${app.output-folder:outputs}")
    private String outputFolder;
    
    @Value("${app.temp-folder:temp}")
    private String tempFolder;
    
    @Value("${app.cleanup.retention-hours:24}")
    private int retentionHours;
    
    @Value("${app.cleanup.enabled:true}")
    private boolean cleanupEnabled;
    
    /**
     * 定时清理临时文件
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "${app.cleanup.cron:0 0 2 * * ?}")
    public void cleanupTempFiles() {
        if (!cleanupEnabled) {
            log.info("文件清理功能已禁用");
            return;
        }
        
        log.info("开始执行定时文件清理任务...");
        
        AtomicLong deletedCount = new AtomicLong(0);
        AtomicLong deletedSize = new AtomicLong(0);
        
        try {
            // 清理 uploads 目录中的临时文件
            cleanupDirectory(uploadFolder, deletedCount, deletedSize);
            
            // 清理 temp 目录
            cleanupDirectory(tempFolder, deletedCount, deletedSize);
            
            log.info("文件清理完成，共删除 {} 个文件，释放空间 {} MB", 
                deletedCount.get(), deletedSize.get() / 1024 / 1024);
                
        } catch (Exception e) {
            log.error("文件清理任务执行失败", e);
        }
    }
    
    /**
     * 清理指定目录中超过保留时间的文件
     */
    private void cleanupDirectory(String directoryPath, 
                                 AtomicLong deletedCount, 
                                 AtomicLong deletedSize) {
        try {
            Path directory = Paths.get(directoryPath);
            if (!Files.exists(directory) || !Files.isDirectory(directory)) {
                log.warn("目录不存在或不是目录: {}", directoryPath);
                return;
            }
            
            Instant cutoffTime = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
            
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
                        throws IOException {
                    try {
                        FileTime lastModifiedTime = attrs.lastModifiedTime();
                        Instant fileTime = lastModifiedTime.toInstant();
                        
                        // 如果文件修改时间早于截止时间，则删除
                        if (fileTime.isBefore(cutoffTime)) {
                            long fileSize = attrs.size();
                            Files.delete(file);
                            deletedCount.incrementAndGet();
                            deletedSize.addAndGet(fileSize);
                            log.debug("删除过期文件: {}, 大小: {} bytes, 修改时间: {}", 
                                file, fileSize, fileTime);
                        }
                    } catch (Exception e) {
                        log.warn("删除文件失败: {}", file, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) 
                        throws IOException {
                    log.warn("访问文件失败: {}", file, exc);
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) 
                        throws IOException {
                    // 删除空目录（可选）
                    try {
                        if (Files.list(dir).findAny().isEmpty()) {
                            // 不删除根目录
                            if (!dir.equals(directory)) {
                                Files.delete(dir);
                                log.debug("删除空目录: {}", dir);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("删除目录失败或目录不为空: {}", dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
        } catch (IOException e) {
            log.error("清理目录失败: {}", directoryPath, e);
        }
    }
    
    /**
     * 手动触发清理（用于管理接口）
     */
    public CleanupResult manualCleanup() {
        AtomicLong deletedCount = new AtomicLong(0);
        AtomicLong deletedSize = new AtomicLong(0);
        
        cleanupDirectory(uploadFolder, deletedCount, deletedSize);
        cleanupDirectory(tempFolder, deletedCount, deletedSize);
        
        return new CleanupResult(deletedCount.get(), deletedSize.get());
    }
    
    /**
     * 清理结果
     */
    public static class CleanupResult {
        private final long deletedCount;
        private final long deletedSize;
        
        public CleanupResult(long deletedCount, long deletedSize) {
            this.deletedCount = deletedCount;
            this.deletedSize = deletedSize;
        }
        
        public long getDeletedCount() { return deletedCount; }
        public long getDeletedSize() { return deletedSize; }
    }
}

