package com.stock.databus.collector;

import com.stock.databus.entity.StockNews;
import com.stock.databus.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 新闻数据采集器
 * 负责从各大财经网站采集股票相关新闻，并进行初步的清洗处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCollector {

    private final NewsRepository newsRepository;

    // 编译一次以提高性能
    private static final Pattern TITLE_PATTERN = Pattern.compile(".*\\d{6}.*"); // 包含股票代码的新闻标题

    /**
     * 采集特定股票代码相关的新闻
     */
    public List<StockNews> collectStockNews(String stockCode) {
        log.info("开始采集股票 {} 相关新闻", stockCode);
        
        List<StockNews> collectedNews = new ArrayList<>();

        try {
            // 采集东方财富网的资讯
            collectedNews.addAll(collectEastMoneyNews(stockCode));
            Thread.sleep(1000); // 避免过于频繁的请求

            // 采集同花顺财经新闻
            collectedNews.addAll(collectiFinDNews(stockCode));
            Thread.sleep(1000);

            // 采集新浪财经新闻
            collectedNews.addAll(collectSinaFinanceNews(stockCode));

            log.info("股票 {} 采集到 {} 条新闻", stockCode, collectedNews.size());
            
        } catch (Exception e) {
            log.error("采集股票 {} 新闻时出现异常", stockCode, e);
        }

        return collectedNews;
    }

    /**
     * 采集东方财富网的资讯信息
     */
    private List<StockNews> collectEastMoneyNews(String stockCode) {
        List<StockNews> newsList = new ArrayList<>();
        
        try {
            String url = "https://so.eastmoney.com/news/s?keyword=" + stockCode;
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            
            Elements newsItems = doc.select(".news-item"); // 选择新闻项目
            for (Element element : newsItems) {
                Element titleElement = element.selectFirst("h3 a");
                if (titleElement != null) {
                    String title = titleElement.text().trim();
                    String link = titleElement.attr("href");
                    
                    Element sourceElement = element.selectFirst(".source");
                    String source = sourceElement != null ? sourceElement.text().trim() : "东方财富网";
                    
                    Element timeElement = element.selectFirst(".time");
                    String pubTimeStr = timeElement != null ? timeElement.text().trim() : getCurrentTimeString();
                    
                    // 创建并清洗新闻对象
                    StockNews news = createNewsObject(stockCode, title, "", source, link, pubTimeStr);
                    if (isValidNews(news)) {
                        cleanAndNormalizeNews(news);
                        newsList.add(news);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("采集东方财富网新闻失败，股票代码: {}, 错误: {}", stockCode, e.getMessage());
        } catch (Exception e) {
            log.error("采集东方财富网新闻时出现未知错误，股票代码: {}", stockCode, e);
        }
        
        return newsList;
    }

    /**
     * 采集同花顺财经新闻
     */
    private List<StockNews> collectiFinDNews(String stockCode) {
        List<StockNews> newsList = new ArrayList<>();
        
        try {
            String url = "https://news.10jqka.com.cn/today/" + stockCode + "/";
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            
            Elements newsItems = doc.select(".arc-list .item");
            for (Element element : newsItems) {
                Element titleElement = element.selectFirst("h2 a");
                if (titleElement != null) {
                    String title = titleElement.text().trim();
                    String link = titleElement.attr("href");
                    
                    Element summaryElement = element.selectFirst(".desc");
                    String content = summaryElement != null ? summaryElement.text().trim() : "";
                    
                    // 提取时间 - 不同网站可能有不同的选择器
                    Element timeElement = element.selectFirst(".time");
                    String pubTimeStr = timeElement != null ? timeElement.text().trim() : getCurrentTimeString();
                    
                    // 创建并清洗新闻对象
                    StockNews news = createNewsObject(stockCode, title, content, "同花顺财经", link, pubTimeStr);
                    if (isValidNews(news)) {
                        cleanAndNormalizeNews(news);
                        newsList.add(news);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("采集同花顺财经新闻失败，股票代码: {}, 错误: {}", stockCode, e.getMessage());
        } catch (Exception e) {
            log.error("采集同花顺财经新闻时出现未知错误，股票代码: {}", stockCode, e);
        }
        
        return newsList;
    }

    /**
     * 采集新浪财经新闻
     */
    private List<StockNews> collectSinaFinanceNews(String stockCode) {
        List<StockNews> newsList = new ArrayList<>();
        
        try {
            String url = "https://finance.sina.com.cn/stock/" + stockCode + "/";
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            
            Elements newsItems = doc.select(".news-item");
            for (Element element : newsItems) {
                Element titleElement = element.selectFirst("h2 a");
                if (titleElement != null) {
                    String title = titleElement.text().trim();
                    String link = titleElement.attr("href");
                    
                    Element summaryElement = element.selectFirst(".summary");
                    String content = summaryElement != null ? summaryElement.text().trim() : "";
                    
                    Element timeElement = element.selectFirst(".time");
                    String pubTimeStr = timeElement != null ? timeElement.text().trim() : getCurrentTimeString();
                    
                    // 创建并清洗新闻对象
                    StockNews news = createNewsObject(stockCode, title, content, "新浪财经", link, pubTimeStr);
                    if (isValidNews(news)) {
                        cleanAndNormalizeNews(news);
                        newsList.add(news);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("采集新浪财经新闻失败，股票代码: {}, 错误: {}", stockCode, e.getMessage());
        } catch (Exception e) {
            log.error("采集新浪财经新闻时出现未知错误，股票代码: {}", stockCode, e);
        }
        
        return newsList;
    }

    /**
     * 创建新闻实体对象
     */
    private StockNews createNewsObject(String stockCode, String title, String content, 
                                     String source, String url, String pubTimeStr) {
        StockNews news = new StockNews();
        news.setStockCode(stockCode);
        news.setTitle(title);
        news.setContent(content);
        news.setSource(source);
        news.setUrl(url);
        news.setPubTime(pubTimeStr);
        news.setCreateTime(LocalDateTime.now());
        return news;
    }

    /**
     * 清洗和规范化新闻数据
     */
    private void cleanAndNormalizeNews(StockNews news) {
        // 清洗标题
        if (news.getTitle() != null) {
            news.setTitle(news.getTitle().replaceAll("\\s+", " ").trim());
            // 去除多余标点和特殊字符
            news.setTitle(news.getTitle().replaceAll("[\\x00-\\x1f\\x7f]", ""));
        }

        // 清洗内容
        if (news.getContent() != null) {
            news.setContent(news.getContent().replaceAll("\\s+", " ").trim());
            // 去除不可见字符
            news.setContent(news.getContent().replaceAll("[\\x00-\\x1f\\x7f]", ""));
        }

        // 清洗来源
        if (news.getSource() != null) {
            news.setSource(news.getSource().trim());
        }

        // 验证URL - 如果不是完整的URL，添加协议
        if (news.getUrl() != null && !news.getUrl().startsWith("http")) {
            if (news.getUrl().startsWith("//")) {
                news.setUrl("https:" + news.getUrl());
            } else {
                news.setUrl("https://" + news.getUrl());
            }
        }
    }

    /**
     * 验证新闻数据是否有效
     */
    private boolean isValidNews(StockNews news) {
        // 检查必填字段
        if (news.getTitle() == null || news.getTitle().trim().isEmpty()) {
            log.debug("新闻标题为空，跳过: {}", news.getStockCode());
            return false;
        }

        // 检查标题是否过短或过长
        String title = news.getTitle().trim();
        if (title.length() < 5 || title.length() > 200) {
            log.debug("新闻标题长度不符合要求，跳过 [{}]: {}", news.getStockCode(), title.substring(0, Math.min(20, title.length())));
            return false;
        }

        // 检查是否包含股票代码
        if (news.getStockCode() != null && !title.contains(news.getStockCode())) {
            log.debug("新闻标题不包含股票代码: {}", news.getStockCode());
        }

        return true;
    }

    /**
     * 保存单个新闻到数据库
     */
    public void saveNews(StockNews news) {
        try {
            newsRepository.save(news);
            log.debug("保存新闻成功: {} - {}", news.getStockCode(), news.getTitle());
        } catch (Exception e) {
            log.error("保存新闻失败: {}", news.getTitle(), e);
        }
    }

    /**
     * 保存多个新闻到数据库
     */
    public void saveBatchNews(List<StockNews> newsList) {
        if (newsList != null && !newsList.isEmpty()) {
            try {
                newsRepository.saveAll(newsList);
                log.info("批量保存新闻成功，共 {} 条", newsList.size());
            } catch (Exception e) {
                log.error("批量保存新闻失败", e);
            }
        }
    }

    /**
     * 获取当前时间字符串
     */
    private String getCurrentTimeString() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}