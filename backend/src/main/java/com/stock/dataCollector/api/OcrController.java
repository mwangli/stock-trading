package com.stock.dataCollector.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * OCR和图片处理控制器 (Mock)
 * 对应前端 /api/ocr/* 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    /**
     * 图片上传接口
     */
    @PostMapping("/uploadImage")
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        log.info("上传文件: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("url", "https://mock-image-url.com/uploaded.png", "text", "Mock OCR Result"));
        
        return ResponseEntity.ok(response);
    }

    /**
     * 文件下载接口 (Mock Excel)
     */
    @GetMapping("/downloadExcel")
    public ResponseEntity<Resource> downloadExcel() {
        log.info("下载Excel文件");
        
        // 生成Mock文件
        byte[] content = "Mock Excel Content".getBytes();
        ByteArrayResource resource = new ByteArrayResource(content);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.length)
                .body(resource);
    }
}
