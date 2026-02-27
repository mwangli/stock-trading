package com.example.aishopping.service.collector;

import com.example.aishopping.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Takealot商品采集器
 * 使用Selenium模拟浏览器内核，从Takealot.com采集商品数据
 *
 * 采集频率: 每5秒获取一个页面
 */
@Service
@Slf4j
public class TakealotCollector {

    private static final String BASE_URL = "https://www.takealot.com";
    private static final int MAX_RETRIES = 3;
    private static final Random random = new Random();

    // 采集频率控制: 每5秒获取一个页面
    private static final long PAGE_DELAY_MS = 5000; // 5秒
    private static final long MIN_DELAY_MS = 3000;  // 最小延迟3秒
    private static final long MAX_DELAY_MS = 5000; // 最大延迟5秒

    @Autowired
    private SeleniumService seleniumService;

    /**
     * 从分类页面采集商品列表
     *
     * @param categoryUrl 分类URL
     * @param maxProducts 最大采集数量
     * @return 商品列表
     */
    public List<Product> collectFromCategory(String categoryUrl, int maxProducts) {
        // 记录开始时间
        long totalStartTime = System.currentTimeMillis();
        java.time.LocalDateTime startDateTime = java.time.LocalDateTime.now();
        
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║              Takealot商品采集任务 - 开始                           ║");
        log.info("╠════════════════════════════════════════════════════════════════╣");
        log.info("║ 任务类型: 单分类采集                                              ║");
        log.info("║ 分类URL: {}", String.format("%-52s", categoryUrl));
        log.info("║ 最大采集数量: {}", String.format("%-52s", maxProducts));
        log.info("║ 采集频率: 每5秒获取1个页面                                        ║");
        log.info("║ 开始时间: {}", String.format("%-52s", startDateTime));
        log.info("╚════════════════════════════════════════════════════════════════╝");

        List<Product> products = new ArrayList<>();

        try {
            // 1. 使用Selenium获取页面
            log.info("[SELENIUM] 正在请求分类页面: {}", categoryUrl);
            long startTime = System.currentTimeMillis();
            String html = seleniumService.getPageContent(categoryUrl);
            long requestTime = System.currentTimeMillis() - startTime;
            log.info("[SELENIUM] 请求完成，响应长度: {} 字符，耗时: {} ms", html.length(), requestTime);

            // 2. 使用Jsoup解析HTML
            log.info("[PARSE] 正在解析HTML内容...");
            Document doc = Jsoup.parse(html);
            
            // 打印页面标题用于调试
            String pageTitle = doc.title();
            log.info("[PARSE] 页面标题: {}", pageTitle);

            // 3. 提取商品列表（根据实际页面结构调整选择器）
            log.info("[PARSE] 正在查找商品元素...");
            // Takealot 使用 React，商品元素有 data-testid 属性
            Elements productElements = doc.select("[data-testid*=product], div[data-product], section.product-card, li.product-card, div[class*=product-card]");
            log.info("[PARSE] 找到 {} 个商品元素", productElements.size());
            
            // 打印HTML片段用于调试
            if (!productElements.isEmpty()) {
                log.info("[PARSE] 第一个商品元素HTML片段: {}", productElements.first().html().substring(0, Math.min(500, productElements.first().html().length())));
            } else {
                log.warn("[PARSE] 未找到任何商品元素!");
                log.info("[PARSE] 页面body前1000字符: {}", doc.body().html().substring(0, Math.min(1000, doc.body().html().length())));
            }

            int count = 0;
            for (Element element : productElements) {
                if (count >= maxProducts) {
                    log.info("[PARSE] 已达到最大采集数量: {}", maxProducts);
                    break;
                }

                try {
                    // 4. 解析商品基本信息
                    log.debug("[PARSE] 正在解析第 {} 个商品...", count + 1);
                    Product product = parseProductFromList(element, categoryUrl);
                    
                    if (product != null && isValidProduct(product)) {
                        products.add(product);
                        count++;
                        log.info("[PARSE] ✓ 成功解析商品 {}: TSIN={}, 标题={}, 价格={}, 品牌={}", 
                            count, product.getTsin(), product.getProductTitle(), product.getPrice(), product.getBrand());
                    } else {
                        log.warn("[PARSE] ✗ 商品无效或解析失败");
                    }
                } catch (Exception e) {
                    log.warn("[PARSE] ✗ 解析商品元素失败: {}", e.getMessage());
                }

                // 5. 添加延迟，避免触发反爬（每5秒获取一个页面）
                try {
                    long delay = MIN_DELAY_MS + random.nextLong(MAX_DELAY_MS - MIN_DELAY_MS);
                    log.info("[COLLECT] 等待 {} ms 后继续采集下一商品...", delay);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 计算总耗时
            long totalTime = System.currentTimeMillis() - totalStartTime;
            java.time.LocalDateTime endDateTime = java.time.LocalDateTime.now();
            double totalMinutes = totalTime / 60000.0;
            
            log.info("╔════════════════════════════════════════════════════════════════╗");
            log.info("║              Takealot商品采集任务 - 完成                          ║");
            log.info("╠════════════════════════════════════════════════════════════════╣");
            log.info("║ 分类URL: {}", String.format("%-52s", categoryUrl));
            log.info("║ 成功采集: {} 个商品", String.format("%-52s", products.size()));
            log.info("║ 页面采集速率: 约每5秒1页                                         ║");
            log.info("║ 开始时间: {}", String.format("%-52s", startDateTime));
            log.info("║ 结束时间: {}", String.format("%-52s", endDateTime));
            log.info("║ 总耗时: {} ms ({:.2f} 分钟)", String.format("%-47s", totalTime), totalMinutes);
            log.info("╚════════════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            log.error("╔════════════════════════════════════════════════════════════════╗");
            log.error("║              Takealot商品采集任务 - 失败                          ║");
            log.error("╠════════════════════════════════════════════════════════════════╣");
            log.error("║ 分类: {}", String.format("%-52s", categoryUrl));
            log.error("║ 错误类型: {}", String.format("%-52s", e.getClass().getName()));
            log.error("║ 错误信息: {}", String.format("%-52s", e.getMessage()));
            log.error("╚════════════════════════════════════════════════════════════════╝");
            log.error("堆栈: ", e);
            throw new RuntimeException("采集分类失败: " + categoryUrl, e);
        }

        return products;
    }
    
    /**
     * 采集多页商品数据
     * 
     * @param baseCategoryUrl 分类基础URL
     * @param maxPages 最大页数
     * @param productsPerPage 每页商品数
     * @return 商品列表
     */
    public List<Product> collectMultiplePages(String baseCategoryUrl, int maxPages, int productsPerPage) {
        // 记录开始时间
        long totalStartTime = System.currentTimeMillis();
        java.time.LocalDateTime startDateTime = java.time.LocalDateTime.now();
        
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║              Takealot商品采集任务 - 多页采集                      ║");
        log.info("╠════════════════════════════════════════════════════════════════╣");
        log.info("║ 任务类型: 多页采集                                               ║");
        log.info("║ 分类URL: {}", String.format("%-52s", baseCategoryUrl));
        log.info("║ 最大页数: {}", String.format("%-52s", maxPages));
        log.info("║ 每页商品数: {}", String.format("%-52s", productsPerPage));
        log.info("║ 采集频率: 每5秒获取1个页面                                       ║");
        log.info("║ 开始时间: {}", String.format("%-52s", startDateTime));
        log.info("╚════════════════════════════════════════════════════════════════╝");

        List<Product> allProducts = new ArrayList<>();
        
        try {
            for (int page = 1; page <= maxPages; page++) {
                long pageStartTime = System.currentTimeMillis();
                
                // 构建分页URL (Takealot分页参数)
                String pageUrl = buildPageUrl(baseCategoryUrl, page);
                
                log.info("");
                log.info("╔════════════════════════════════════════════════════════════════╗");
                log.info("║ 正在采集第 {} / {} 页", String.format("%-3s", page), String.format("%-47s", maxPages));
                log.info("║ 页面URL: {}", String.format("%-52s", pageUrl));
                log.info("╚════════════════════════════════════════════════════════════════╝");
                
                // 使用Selenium获取页面
                log.info("[SELENIUM] 正在请求页面 {}: {}", page, pageUrl);
                long requestStartTime = System.currentTimeMillis();
                String html = seleniumService.getPageContent(pageUrl);
                long requestTime = System.currentTimeMillis() - requestStartTime;
                log.info("[SELENIUM] 页面 {} 请求完成，响应长度: {} 字符，耗时: {} ms", page, html.length(), requestTime);
                
                // 解析HTML
                Document doc = Jsoup.parse(html);
                String pageTitle = doc.title();
                log.info("[PARSE] 页面标题: {}", pageTitle);
                
                // 提取商品列表
                Elements productElements = doc.select("div.product-card, section.product-card, li.product-card");
                log.info("[PARSE] 页面 {} 找到 {} 个商品元素", page, productElements.size());
                
                int pageProductCount = 0;
                for (Element element : productElements) {
                    if (pageProductCount >= productsPerPage || allProducts.size() >= maxPages * productsPerPage) {
                        break;
                    }
                    
                    try {
                        Product product = parseProductFromList(element, pageUrl);
                        if (product != null && isValidProduct(product)) {
                            allProducts.add(product);
                            pageProductCount++;
                            log.info("[PARSE] ✓ 第{}页商品 {}: TSIN={}, 标题={}", 
                                page, pageProductCount, product.getTsin(), product.getProductTitle());
                        }
                    } catch (Exception e) {
                        log.warn("[PARSE] ✗ 解析商品元素失败: {}", e.getMessage());
                    }
                }
                
                long pageTime = System.currentTimeMillis() - pageStartTime;
                log.info("[COLLECT] 第 {} 页采集完成，商品数: {}，耗时: {} ms", page, pageProductCount, pageTime);
                
                // 页面之间添加5秒延迟
                if (page < maxPages) {
                    log.info("[COLLECT] 等待 {} ms 后采集下一页...", PAGE_DELAY_MS);
                    Thread.sleep(PAGE_DELAY_MS);
                }
            }
            
            // 计算总耗时
            long totalTime = System.currentTimeMillis() - totalStartTime;
            java.time.LocalDateTime endDateTime = java.time.LocalDateTime.now();
            double totalMinutes = totalTime / 60000.0;
            
            log.info("");
            log.info("╔════════════════════════════════════════════════════════════════╗");
            log.info("║              Takealot商品采集任务 - 完成                          ║");
            log.info("╠════════════════════════════════════════════════════════════════╣");
            log.info("║ 分类URL: {}", String.format("%-52s", baseCategoryUrl));
            log.info("║ 采集页数: {} / {}", String.format("%-52s", maxPages), maxPages);
            log.info("║ 成功采集: {} 个商品", String.format("%-52s", allProducts.size()));
            log.info("║ 采集频率: 约每5秒1页                                            ║");
            log.info("║ 开始时间: {}", String.format("%-52s", startDateTime));
            log.info("║ 结束时间: {}", String.format("%-52s", endDateTime));
            log.info("║ 总耗时: {} ms ({:.2f} 分钟)", String.format("%-47s", totalTime), totalMinutes);
            log.info("╚════════════════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            log.error("╔════════════════════════════════════════════════════════════════╗");
            log.error("║              Takealot商品采集任务 - 失败                          ║");
            log.error("╠════════════════════════════════════════════════════════════════╣");
            log.error("║ 分类: {}", String.format("%-52s", baseCategoryUrl));
            log.error("║ 错误类型: {}", String.format("%-52s", e.getClass().getName()));
            log.error("║ 错误信息: {}", String.format("%-52s", e.getMessage()));
            log.error("╚════════════════════════════════════════════════════════════════╝");
            log.error("堆栈: ", e);
            throw new RuntimeException("多页采集失败: " + baseCategoryUrl, e);
        }
        
        return allProducts;
    }
    
    /**
     * 构建分页URL
     */
    private String buildPageUrl(String baseUrl, int page) {
        if (baseUrl.contains("?")) {
            return baseUrl + "&page=" + page;
        } else {
            return baseUrl + "?page=" + page;
        }
    }

    /**
     * 从列表页解析商品基本信息
     */
    private Product parseProductFromList(Element element, String categoryUrl) {
        Product product = new Product();

        try {
            // 提取商品URL
            String productUrl = element.select("a.product-anchor, a[href*=/product/], a.pdp-link").attr("href");
            if (!productUrl.startsWith("http")) {
                productUrl = BASE_URL + productUrl;
            }
            product.setProductUrl(productUrl);

            // 提取TSIN
            String tsin = extractTsinFromUrl(productUrl);
            product.setTsin(tsin);

            // 提取标题
            String title = element.select("h4.product-title, h3.product-title, [class*=title]").first().text();
            product.setProductTitle(title);

            // 提取价格
            String priceText = element.select("span.price, [class*=price]").first().text();
            product.setPrice(parsePrice(priceText));

            // 提取图片
            String imageUrl = element.select("img.product-image, img[class*=product], img[data-track]").attr("src");
            if (StringUtils.isNotBlank(imageUrl)) {
                product.setImageUrls("[\"" + imageUrl + "\"]");
            }

            // 提取品牌
            String brand = element.select("span.brand, [class*=brand]").text();
            if (StringUtils.isBlank(brand)) {
                brand = extractBrandFromTitle(title);
            }
            product.setBrand(brand);

            // 设置分类
            product.setMainCategory(extractCategoryFromUrl(categoryUrl));

            // 设置采集时间
            product.setCollectedAt(LocalDateTime.now());

            // 默认货币
            product.setCurrency("ZAR");

        } catch (Exception e) {
            log.warn("解析商品列表元素失败: {}", e.getMessage());
            return null;
        }

        return product;
    }

    /**
     * 解析商品详情页（可选，获取更完整的信息）
     */
    public Product parseProductDetail(Product product, String detailUrl) {
        try {
            String html = seleniumService.getPageContent(detailUrl);
            Document doc = Jsoup.parse(html);

            // 提取品牌
            String brand = doc.select("span.brand-name, [data-track=brand]").text();
            if (StringUtils.isNotBlank(brand)) {
                product.setBrand(brand);
            }

            // 提取描述
            String description = doc.select("div.product-description, [class*=description]").text();
            product.setDescription(description);

            // 提取评分
            String ratingText = doc.select("span.rating-value, [class*=rating]").text();
            product.setRating(parseRating(ratingText));

            // 提取评论数
            String reviewCountText = doc.select("span.review-count, [class*=reviews]").text();
            product.setReviewCount(parseReviewCount(reviewCountText));

            // 提取更多图片
            Elements imageElements = doc.select("img.gallery-image, img[data-zoom]");
            List<String> imageUrls = imageElements.stream()
                    .map(e -> e.attr("src"))
                    .limit(5)
                    .collect(Collectors.toList());
            if (!imageUrls.isEmpty()) {
                product.setImageUrls(imageUrls.toString());
            }

            // 提取保修信息
            String warrantyType = doc.select("span.warranty-type").text();
            product.setWarrantyType(warrantyType);

        } catch (Exception e) {
            log.warn("解析详情页失败: {}", detailUrl, e);
        }

        return product;
    }

    /**
     * 从URL中提取TSIN
     */
    private String extractTsinFromUrl(String url) {
        // 尝试匹配各种TSIN格式
        Pattern pattern = Pattern.compile("(PLID|TSIN|prod_|product-)([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(2).toUpperCase();
        }
        // 生成唯一ID
        return "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * 从标题中提取品牌
     */
    private String extractBrandFromTitle(String title) {
        if (StringUtils.isBlank(title)) {
            return "Unknown";
        }

        // 常见品牌关键词
        String[] commonBrands = {"Samsung", "Apple", "Sony", "LG", "Dell", "HP", "Lenovo",
                "Asus", "Acer", "Canon", "Nikon", "JBL", "Bose", "Beats"};

        for (String brand : commonBrands) {
            if (title.toLowerCase().contains(brand.toLowerCase())) {
                return brand;
            }
        }

        // 返回第一个单词作为品牌
        String[] words = title.split("\\s+");
        return words.length > 0 ? words[0] : "Unknown";
    }

    /**
     * 从URL中提取分类
     */
    private String extractCategoryFromUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return "Unknown";
        }

        // 从URL中提取分类名称
        if (url.contains("electronics")) {
            return "Electronics";
        } else if (url.contains("cellphones")) {
            return "Cellphones";
        } else if (url.contains("laptops")) {
            return "Laptops";
        } else if (url.contains("cameras")) {
            return "Cameras";
        } else if (url.contains("audio")) {
            return "Audio";
        } else if (url.contains("gaming")) {
            return "Gaming";
        }

        return "General";
    }

    /**
     * 解析价格
     */
    private BigDecimal parsePrice(String priceText) {
        if (StringUtils.isBlank(priceText)) {
            return BigDecimal.ZERO;
        }

        try {
            // 移除货币符号和逗号
            String cleanPrice = priceText.replaceAll("[R\\s,]|[A-Za-z]", "");
            return new BigDecimal(cleanPrice.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 解析评分
     */
    private BigDecimal parseRating(String ratingText) {
        if (StringUtils.isBlank(ratingText)) {
            return null;
        }

        try {
            String cleanRating = ratingText.replaceAll("[^0-9.]", "");
            return new BigDecimal(cleanRating);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析评论数
     */
    private Integer parseReviewCount(String reviewText) {
        if (StringUtils.isBlank(reviewText)) {
            return 0;
        }

        try {
            // 提取数字
            String numbers = reviewText.replaceAll("[^0-9]", "");
            return Integer.parseInt(numbers);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 验证商品是否有效
     */
    private boolean isValidProduct(Product product) {
        return product != null
                && StringUtils.isNotBlank(product.getProductTitle())
                && StringUtils.isNotBlank(product.getTsin());
    }
}
