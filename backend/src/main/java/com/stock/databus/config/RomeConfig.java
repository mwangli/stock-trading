package com.stock.databus.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * ROME 框架配置 - 允许 DOCTYPE
 */
@Slf4j
@Configuration
public class RomeConfig {

    @PostConstruct
    public void init() {
        log.info("=== 配置 ROME 框架允许 DOCTYPE ===");
        
        // 允许访问外部 DTD（解析 RSS 必需）
        System.setProperty("javax.xml.accessExternalDTD", "all");
        System.setProperty("javax.xml.accessExternalSchema", "all");
        System.setProperty("javax.xml.stream.supportDTD", "true");
        
        // 禁用严格 XXE 保护（为了兼容 RSS 源）
        System.setProperty("rome.enable.secureProcessing", "false");
        System.setProperty("com.sun.xml.bind.v2.external.runtime.allowDoctypeDecl", "true");
        
        log.info("ROME 配置完成，现在可以解析包含 DOCTYPE 的 RSS 源");
        log.info("系统属性:");
        log.info("  - javax.xml.accessExternalDTD = all");
        log.info("  - javax.xml.stream.supportDTD = true");
        log.info("  - rome.enable.secureProcessing = false");
    }
}