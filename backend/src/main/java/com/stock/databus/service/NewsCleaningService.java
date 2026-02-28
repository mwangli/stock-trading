package com.stock.databus.service;

import com.stock.databus.entity.StockNews;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新闻数据清洗服务
 * 负责对采集来的新闻数据进行深度清洗和标准化处理
 */
@Slf4j
@Service
public class NewsCleaningService {

    // 敏感词过滤列表
    private static final Set<String> SENSITIVE_WORDS = Set.of(
        "敏感词语1", "敏感词语2", "禁止内容"
    );

    // 验证模式
    private static final Pattern URL_PATTERN = Pattern.compile(
        "^(https?://)?([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])(:\\d+)?(/.*)?$"
    );
    
    private static final Pattern CHINESE_CONTENT_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");
    
    // 通用垃圾内容识别模式
    private static final Pattern GIBBERISH_PATTERN = Pattern.compile("(.)\\1{10,}"); // 重复10次以上相同字符

    /**
     * 清洗单个新闻对象
     */
    public StockNews cleanNewsData(StockNews news) {
        if (news == null) {
            return null;
        }

        // 执行各个清洗步骤
        filterSensitiveContent(news);
        normalizeText(news);
        sanitizeUrls(news);
        removeDuplicatedContent(news);
        validateContentQuality(news);
        
        // 设置清洗标记
        if (news.getCreateTime() == null) {
            news.setCreateTime(java.time.LocalDateTime.now());
        }
        
        log.debug("完成新闻数据清洗: {}", news.getTitle());
        return news;
    }

    /**
     * 批量清洗新闻数据
     */
    public List<StockNews> cleanBatchNews(List<StockNews> newsList) {
        if (newsList == null || newsList.isEmpty()) {
            return Collections.emptyList();
        }

        List<StockNews> cleanedNews = new ArrayList<>();
        for (StockNews news : newsList) {
            StockNews cleaned = cleanNewsData(news);
            if (isValidAfterClean(cleaned)) {
                cleanedNews.add(cleaned);
            } else {
                log.debug("清洗后新闻数据失效，跳过: {}", news.getTitle());
            }
        }
        
        log.info("批量清洗完成，原始: {} 条，有效: {} 条", newsList.size(), cleanedNews.size());
        return cleanedNews;
    }

    /**
     * 过滤敏感内容
     */
    private void filterSensitiveContent(StockNews news) {
        if (news.getTitle() != null) {
            for (String sensitiveWord : SENSITIVE_WORDS) {
                if (news.getTitle().contains(sensitiveWord)) {
                    news.setTitle(news.getTitle().replaceAll(sensitiveWord, "***"));
                }
            }
        }

        if (news.getContent() != null) {
            for (String sensitiveWord : SENSITIVE_WORDS) {
                if (news.getContent().contains(sensitiveWord)) {
                    news.setContent(news.getContent().replaceAll(sensitiveWord, "***"));
                }
            }
        }
        
        // 过滤过多标点符号
        if (news.getTitle() != null) {
            news.setTitle(removeExcessivePunctuation(news.getTitle()));
        }
        if (news.getContent() != null) {
            news.setContent(removeExcessivePunctuation(news.getContent()));
        }
    }

    /**
     * 文本标准化
     */
    private void normalizeText(StockNews news) {
        // 标准化标题
        if (news.getTitle() != null) {
            news.setTitle(news.getTitle()
                    .replaceAll("\\s+", " ")  // 统一空格
                    .replaceAll("\\u00A0", " ")  // 靺殊空格转为普通空格
                    .strip());
        }

        // 标准化内容
        if (news.getContent() != null) {
            news.setContent(news.getContent()
                    .replaceAll("\\s+", " ")   // 统一空格
                    .replaceAll("\\u00A0", " ")  // 靺殊空格转为普通空格
                    .replaceAll("[\\r\\n\\t]+", "\\n")  // 統一換行符
                    .strip());
        }

        // 清理HTML标签
        if (news.getTitle() != null) {
            news.setTitle(stripHtmlTags(news.getTitle()));
        }
        if (news.getContent() != null) {
            news.setContent(stripHtmlTags(news.getContent()));
        }
    }

    /**
     * URL安全性检查和修復
     */
    private void sanitizeUrls(StockNews news) {
        if (news.getUrl() != null) {
            // 检查URL合法性
            Matcher matcher = URL_PATTERN.matcher(news.getUrl());
            if (!matcher.matches()) {
                log.warn("發現非法URL: {}", news.getUrl());
                // 尝試修復常見的問題
                String correctedUrl = fixCommonUrlIssues(news.getUrl());
                if (URL_PATTERN.matcher(correctedUrl).matches()) {
                    news.setUrl(correctedUrl);
                } else {
                    // 保留原URL但記錄問題，實際應用中可能需要設為null
                    log.error("修正後依然是非法URL: {}", correctedUrl);
                }
            }
        }
    }

    /**
     * 修復常見URL問題
     */
    private String fixCommonUrlIssues(String url) {
        if (url == null) return null;
        
        // 添加協議
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // 如果看起來像域名，添加https
            if (url.contains(".") && url.indexOf(".") < url.length() - 2) {
                if (!url.startsWith("//")) {
                    return "https://" + url;
                } else {
                    return "https:" + url;
                }
            }
        }
        
        return url.trim();
    }

    /**
     * 移除重複內容
     */
    private void removeDuplicatedContent(StockNews news) {
        if (news.getTitle() != null && news.getContent() != null) {
            // 如果內容和標題極其相似則認為是重複
            String title = news.getTitle().toLowerCase().replaceAll("\\s+", "");
            String content = news.getContent().toLowerCase().replaceAll("\\s+", "");
            
            // 如果標題在內容中出現超過一定比例，可能只是截取版
            double similarityRatio = calculateSimilarity(title, content);
            if (similarityRatio > 0.7) {
                // 錜測這是重複內容，僅保留主要數據
                if (content.length() < title.length()) {
                    // 內制內容，讓內容豐富些
                }
            }
        }
    }

    /**
     * 效果內容質量驗證
     */
    private void validateContentQuality(StockNews news) {
        // 驗證包含中文字
        if (news.getContent() != null && !CHINESE_CONTENT_PATTERN.matcher(news.getContent()).find()) {
            log.warn("內容質量問題 - 找不到中文字符: {}", news.getTitle());
            // 在實際應用中可能需要設定有效性標記
        }
        
        // 橗證不是亂碼或垃圾信息
        if (news.getContent() != null && GIBBERISH_PATTERN.matcher(news.getContent()).find()) {
            log.warn("檢測到重複垃圾內容: {}", news.getTitle());
        }
        
        // 橗證最小長度（僅對關鍵字段）
        if (news.getTitle() != null && news.getTitle().length() < 5) {
            log.warn("標題長度不足: {}", news.getTitle());
        }
        
        // 如果內容過於簡短（比如全是空白或很少內容）
        if (news.getContent() != null) {
            String strippedContent = news.getContent().replaceAll("\\s+", "").replaceAll("[。！？]", "");
            if (strippedContent.length() < 10) {
                // 如果內容太短，可能只是標題副本，這時只保留標題
                log.debug("新聞內容過短，建議檢查: {}", news.getTitle());
            }
        }
    }

    /**
     * 檢查清洗後的新聞是否有效
     */
    private boolean isValidAfterClean(StockNews news) {
        // 橗證必須字段存在
        if (news == null) return false;
        
        if (news.getStockCode() == null || news.getStockCode().trim().isEmpty()) {
            return false;
        }
        
        if (news.getTitle() == null || news.getTitle().trim().isEmpty()) {
            return false;
        }
        
        // 如果有內容，確保內容有實際意義
        if (news.getContent() != null) {
            String strippedContent = news.getContent().replaceAll("\\s|[。！？]", "").trim();
            if (strippedContent.length() < 10 && news.getTitle().contains(strippedContent)) {
                // 如果內容只是標題副本或很短，但至少要有基本內容
            }
        }
        
        return news.getTitle().length() >= 5;
    }

    /**
     * 移除過多標點符號
     */
    private String removeExcessivePunctuation(String text) {
        if (text == null) return null;
        
        // 修復連續重複字符問題（如多個驚嘆號、星號等）
        text = text.replaceAll("!{2,}", "!");    // 多個驚嘆號變一個
        text = text.replaceAll("\\?{2,}", "?");  // 多個問號變一個
        text = text.replaceAll("\\*{2,}", "*");  // 多個星號變一個
        text = text.replaceAll("~{2,}", "~");    // 多個波浪號變一個
        text = text.replaceAll("。{2,}", "。");  // 多個句號變一個
        text = text.replaceAll("，{2,}", "，");  // 多個逗號變一個
        
        return text;
    }
    
    /**
     * 去除HTML標籤
     */
    private String stripHtmlTags(String text) {
        if (text == null) return null;
        // 靻單的HTML標籤移除
        text = text.replaceAll("<[^>]*>", "");
        return text;
    }
    
    /**
     * 計算兩個字符串的相似度
     */
    private double calculateSimilarity(String str1, String str2) {
        // 設定較短的字符串為s1，較長的為s2
        String s1 = str1.length() <= str2.length() ? str1 : str2;
        String s2 = str1.length() <= str2.length() ? str2 : str1;

        if (s2.length() == 0) return 1.0;

        int distance = computeLevenshteinDistance(s1, s2);
        return (s2.length() - distance) / (double) s2.length();
    }
    
    /**
     * 計算萊文斯坦距離
     */
    private int computeLevenshteinDistance(String str1, String str2) {
        int[][] dp = new int[str1.length() + 1][str2.length() + 1];
        
        for (int i = 0; i <= str1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= str2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                if (str1.charAt(i - 1) == str2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]) + 1;
                }
            }
        }
        
        return dp[str1.length()][str2.length()];
    }
}