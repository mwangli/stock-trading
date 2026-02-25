package com.example.aishopping.service.collector;

import com.example.aishopping.entity.CollectionLog;
import com.example.aishopping.entity.CollectionTask;
import com.example.aishopping.entity.Product;
import com.example.aishopping.mapper.CollectionLogMapper;
import com.example.aishopping.mapper.ProductMapper;
import com.example.aishopping.service.ProductService;
import com.example.aishopping.websocket.CollectionLogWebSocket;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 商品采集核心服务
 * 负责协调采集流程：获取商品 -> 品牌过滤 -> 保存到数据库 -> 记录日志
 */
@Service
@Slf4j
public class ProductCollectorService {

    @Autowired
    private TakealotCollector takealotCollector;

    @Autowired
    private BrandFilterService brandFilterService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private CollectionLogMapper collectionLogMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 异步执行采集任务
     *
     * @param task 采集任务
     * @return 采集结果
     */
    @Async("taskExecutor")
    public CompletableFuture<CollectionResult> collectProducts(CollectionTask task) {
        log.info("开始执行采集任务: {}, 类型: {}", task.getTaskName(), task.getTaskType());
        sendWsLog(task.getId(), "INFO", "开始执行采集任务: " + task.getTaskName());

        CollectionResult result = new CollectionResult();
        result.setTaskId(task.getId());
        result.setStartTime(LocalDateTime.now());

        try {
            // 1. 解析分类筛选条件
            List<String> categoryUrls = parseCategoryFilter(task.getCategoryFilter());
            if (categoryUrls.isEmpty()) {
                // 使用所有分类，每个分类采集销量前100的商品
                categoryUrls = getAllCategories();
            }

            sendWsLog(task.getId(), "INFO", "准备采集分类数量: " + categoryUrls.size() + ", 每个分类采集100个商品");

            // 2. 遍历分类采集商品
            for (String categoryUrl : categoryUrls) {
                // 每个分类采集销量前100的商品
                int maxProducts = 100;
                sendWsLog(task.getId(), "INFO", "开始采集分类: " + categoryUrl + ", 销量前100商品");
                
                List<Product> products = takealotCollector.collectFromCategory(categoryUrl, maxProducts);
                
                sendWsLog(task.getId(), "INFO", "分类 " + categoryUrl + " 采集完成，获取 " + products.size() + " 个商品");

                // 3. 处理每个商品：过滤、保存、记录日志
                for (Product product : products) {
                    processProduct(product, task, result);
                }
            }

            result.setStatus("COMPLETED");
            String completeMsg = String.format("采集任务完成: 成功=%d, 失败=%d, 过滤=%d", 
                    result.getSuccessCount(), result.getFailedCount(), result.getFilteredCount());
            log.info(completeMsg);
            sendWsLog(task.getId(), "INFO", completeMsg);

        } catch (Exception e) {
            log.error("采集任务执行失败: {}", task.getTaskName(), e);
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            sendWsLog(task.getId(), "ERROR", "采集任务执行失败: " + e.getMessage());
        }

        result.setEndTime(LocalDateTime.now());
        return CompletableFuture.completedFuture(result);
    }

    /**
     * 处理单个商品：品牌过滤 -> 评分过滤 -> 保存/更新 -> 记录日志
     */
    private void processProduct(Product product, CollectionTask task, CollectionResult result) {
        try {
            // 设置任务ID
            product.setTaskId(task.getId());

            // 1. 品牌过滤检查
            if (brandFilterService.isBlacklisted(product.getBrand())) {
                product.setIsFiltered(true);
                product.setFilterReason("品牌 '" + product.getBrand() + "' 在黑名单中");
                product.setBrand("FILTERED");

                // 保存被过滤的商品（可选，取决于业务需求）
                productService.save(product);

                // 记录日志
                saveCollectionLog(task, product, "FILTERED",
                        "品牌被过滤: " + product.getBrand());

                result.incrementFilteredCount();
                return;
            }

            // 2. 评分过滤检查 - 只保留评分大于3的商品
            if (product.getRating() != null && product.getRating().doubleValue() <= 3.0) {
                product.setIsFiltered(true);
                product.setFilterReason("评分 " + product.getRating() + " <= 3");

                // 保存被过滤的商品
                productService.save(product);

                // 记录日志
                saveCollectionLog(task, product, "FILTERED",
                        "评分被过滤: " + product.getRating());

                result.incrementFilteredCount();
                return;
            }

            // 3. 检查是否已存在（基于TSIN去重）
            Product existingProduct = productMapper.selectByTsin(product.getTsin());
            if (existingProduct != null) {
                // 更新已有商品
                product.setId(existingProduct.getId());
                product.setIsFiltered(false);
                product.setFilterReason(null);
                productService.updateById(product);

                // 记录日志
                saveCollectionLog(task, product, "SUCCESS", "更新已有商品, TSIN: " + product.getTsin());
                log.debug("更新商品: {}", product.getProductTitle());
            } else {
                // 保存新商品
                product.setIsFiltered(false);
                productService.save(product);

                // 记录日志
                saveCollectionLog(task, product, "SUCCESS", "新增商品, TSIN: " + product.getTsin());
                log.debug("新增商品: {}", product.getProductTitle());
            }

            result.incrementSuccessCount();

        } catch (Exception e) {
            log.error("处理商品失败: {}, 错误: {}", product.getTsin(), e.getMessage());

            // 记录失败日志
            saveCollectionLog(task, product, "FAILED", e.getMessage());

            result.incrementFailedCount();
        }
    }

    /**
     * 保存采集日志
     */
    private void saveCollectionLog(CollectionTask task, Product product, String status, String message) {
        try {
            CollectionLog log = new CollectionLog();
            log.setTaskId(task.getId());
            log.setTsin(product.getTsin());
            log.setProductTitle(product.getProductTitle());
            log.setProductUrl(product.getProductUrl());
            log.setStatus(status);
            log.setMessage(message);
            log.setCreatedAt(LocalDateTime.now());

            collectionLogMapper.insert(log);
        } catch (Exception e) {
            log.error("保存采集日志失败: {}", e.getMessage());
        }
    }

    /**
     * 发送WebSocket日志
     */
    private void sendWsLog(Long taskId, String level, String message) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("taskId", taskId);
            logData.put("level", level);
            logData.put("message", message);
            logData.put("timestamp", LocalDateTime.now().toString());
            
            String jsonLog = objectMapper.writeValueAsString(logData);
            CollectionLogWebSocket.sendLog(taskId, jsonLog);
        } catch (Exception e) {
            log.warn("发送WebSocket日志失败: {}", e.getMessage());
        }
    }

    /**
     * 获取所有要采集的分类列表
     */
    private List<String> getAllCategories() {
        List<String> categories = new java.util.ArrayList<>();
        
        // Takealot 主要分类
        categories.add("https://www.takealot.com/electronics");
        categories.add("https://www.takealot.com/cellphones");
        categories.add("https://www.takealot.com/computers/laptops");
        categories.add("https://www.takealot.com/tv-audio-entertainment/televisions");
        categories.add("https://www.takealot.com/gaming");
        categories.add("https://www.takealot.com/home-appliances");
        
        return categories;
    }

    /**
     * 解析分类筛选条件
     */
    private List<String> parseCategoryFilter(String categoryFilter) {
        List<String> categoryUrls = new java.util.ArrayList<>();
        
        if (categoryFilter == null || categoryFilter.trim().isEmpty()) {
            return categoryUrls;
        }
        
        // 支持多分类，用逗号分隔
        String[] categories = categoryFilter.split(",");
        for (String category : categories) {
            String trimmed = category.trim();
            if (!trimmed.isEmpty()) {
                // 如果是完整URL直接添加，否则拼接
                if (trimmed.startsWith("http")) {
                    categoryUrls.add(trimmed);
                } else {
                    categoryUrls.add("https://www.takealot.com/" + trimmed);
                }
            }
        }
        
        return categoryUrls;
    }

    /**
     * 采集结果
     */
    @lombok.Data
    public static class CollectionResult {
        private Long taskId;
        private String status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String errorMessage;

        private int successCount = 0;
        private int failedCount = 0;
        private int filteredCount = 0;

        public void incrementSuccessCount() {
            this.successCount++;
        }

        public void incrementFailedCount() {
            this.failedCount++;
        }

        public void incrementFilteredCount() {
            this.filteredCount++;
        }

        public int getTotalCount() {
            return successCount + failedCount + filteredCount;
        }
    }
}
