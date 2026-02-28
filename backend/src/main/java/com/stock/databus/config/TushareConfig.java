package com.stock.databus.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collections;
import java.util.List;

/**
 * Tushare客户端配置
 * 提供缺省值以确保在缺少API密钥的情况下应用也能启动
 */
@Configuration
public class TushareConfig {

    @Bean
    @ConditionalOnMissingBean
    public List<String> tushareApiKeys() {
        // 防御性处理：如果配置文件中没有找到API密钥，则使用空列表
        // 这允许应用在没有API密钥情况下运行，但相应功能会受到影响
        return Collections.emptyList();
    }
}