package online.mwang.stockTrading.clients;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 情感分析服务客户端
 * 调用Python AI服务的情感分析API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentimentClient {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8001}")
    private String baseUrl;

    /**
     * 分析单条文本情感
     */
    public SentimentResponse analyze(String text) {
        try {
            String url = baseUrl + "/api/sentiment/analyze";
            
            Map<String, String> request = new HashMap<>();
            request.put("text", text);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<SentimentResponse> response = restTemplate.postForEntity(
                url, entity, SentimentResponse.class);
            
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to analyze sentiment: {}", e.getMessage());
            return getDefaultResponse();
        }
    }

    /**
     * 批量分析文本情感
     */
    @SuppressWarnings("unchecked")
    public List<SentimentResponse> analyzeBatch(List<String> texts) {
        try {
            String url = baseUrl + "/api/sentiment/analyze/batch";
            
            Map<String, List<String>> request = new HashMap<>();
            request.put("texts", texts);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, List<String>>> entity = new HttpEntity<>(request, headers);
            
            List<SentimentResponse> response = restTemplate.postForObject(url, entity, List.class);
            
            return response != null ? response : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to analyze batch sentiment: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 分析新闻批量情感
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> analyzeNews(List<NewsItem> news) {
        try {
            String url = baseUrl + "/api/sentiment/analyze/news";
            
            Map<String, List<NewsItem>> request = new HashMap<>();
            request.put("news", news);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, List<NewsItem>>> entity = new HttpEntity<>(request, headers);
            
            List<Map<String, Object>> response = restTemplate.postForObject(url, entity, List.class);
            
            return response != null ? response : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to analyze news sentiment: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 分析市场整体情绪
     */
    public MarketSentimentResponse analyzeMarketSentiment(List<NewsItem> news) {
        try {
            String url = baseUrl + "/api/sentiment/market/analyze";
            
            Map<String, List<NewsItem>> request = new HashMap<>();
            request.put("news", news);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, List<NewsItem>>> entity = new HttpEntity<>(request, headers);
            
            return restTemplate.postForObject(url, entity, MarketSentimentResponse.class);
        } catch (Exception e) {
            log.error("Failed to analyze market sentiment: {}", e.getMessage());
            return getDefaultMarketResponse();
        }
    }

    /**
     * 获取模型信息
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getModelInfo() {
        try {
            String url = baseUrl + "/api/sentiment/model/info";
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.error("Failed to get model info: {}", e.getMessage());
            Map<String, Object> info = new HashMap<>();
            info.put("model", "FinBERT (Unavailable)");
            info.put("status", "error");
            return info;
        }
    }

    private SentimentResponse getDefaultResponse() {
        SentimentResponse response = new SentimentResponse();
        response.setLabel("neutral");
        response.setScore(0.0);
        response.setConfidence(0.0);
        Map<String, Double> probs = new HashMap<>();
        probs.put("positive", 0.33);
        probs.put("neutral", 0.34);
        probs.put("negative", 0.33);
        response.setProbabilities(probs);
        return response;
    }

    private MarketSentimentResponse getDefaultMarketResponse() {
        MarketSentimentResponse response = new MarketSentimentResponse();
        response.setOverall("neutral");
        response.setScore(0.0);
        response.setPositiveCount(0);
        response.setNeutralCount(0);
        response.setNegativeCount(0);
        response.setTotalCount(0);
        return response;
    }

    // ==================== DTOs ====================

    @Data
    public static class NewsItem {
        private String title;
        private String content;
        private String url;
        private String publishedAt;
    }

    @Data
    public static class SentimentResponse {
        private String label;
        private Double score;
        private Double confidence;
        private Map<String, Double> probabilities;
    }

    @Data
    public static class MarketSentimentResponse {
        private String overall;
        private Double score;
        private Integer positiveCount;
        private Integer neutralCount;
        private Integer negativeCount;
        private Integer totalCount;
    }
}
