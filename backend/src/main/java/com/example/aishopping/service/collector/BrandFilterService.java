package com.example.aishopping.service.collector;

import com.example.aishopping.entity.BrandBlacklist;
import com.example.aishopping.mapper.BrandBlacklistMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 品牌过滤服务
 * 检查品牌是否在黑名单中
 */
@Service
@Slf4j
public class BrandFilterService {

    @Autowired
    private BrandBlacklistMapper blacklistMapper;

    private Set<String> blacklistCache;

    @PostConstruct
    public void init() {
        refreshCache();
    }

    /**
     * 检查品牌是否在黑名单中
     *
     * @param brand 品牌名称
     * @return true表示在黑名单中，false表示不在
     */
    public boolean isBlacklisted(String brand) {
        if (StringUtils.isBlank(brand)) {
            return false;
        }

        String lowerBrand = brand.toLowerCase().trim();

        // 完全匹配
        if (blacklistCache != null && blacklistCache.contains(lowerBrand)) {
            log.debug("品牌 '{}' 完全匹配黑名单，已过滤", brand);
            return true;
        }

        // 模糊匹配（品牌名称包含黑名单关键词）
        if (blacklistCache != null) {
            for (String blacklistedBrand : blacklistCache) {
                if (lowerBrand.contains(blacklistedBrand) ||
                        blacklistedBrand.contains(lowerBrand)) {
                    log.info("品牌 '{}' 模糊匹配黑名单 '{}'，已过滤", brand, blacklistedBrand);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 刷新黑名单缓存
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000)
    public void refreshCache() {
        try {
            List<BrandBlacklist> blacklist = blacklistMapper.selectActiveBlacklist();
            blacklistCache = blacklist.stream()
                    .map(b -> b.getBrandName().toLowerCase().trim())
                    .collect(Collectors.toSet());

            log.info("品牌黑名单缓存已刷新，共 {} 条记录", blacklistCache.size());
        } catch (Exception e) {
            log.error("刷新品牌黑名单缓存失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取缓存的黑名单数量
     */
    public int getBlacklistCount() {
        return blacklistCache != null ? blacklistCache.size() : 0;
    }
}
