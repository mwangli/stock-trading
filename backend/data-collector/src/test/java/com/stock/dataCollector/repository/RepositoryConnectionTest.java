package com.stock.dataCollector.repository;

import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.entity.StockPrice;
import com.stock.dataCollector.entity.StockNews;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository 测试类
 * 验证MySQL、MongoDB、Redis的连接和读写能力
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RepositoryConnectionTest {

    // ==================== MongoDB 相关 ====================
    
    @Autowired
    private StockRepository stockRepository;
    
    @Autowired
    private PriceRepository priceRepository;
    
    @Autowired
    private NewsRepository newsRepository;
    
    @Autowired
    private MongoTemplate mongoTemplate;

    // ==================== MySQL 相关 ====================
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== Redis 相关 ====================
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 测试数据
    private static final String TEST_STOCK_CODE = "TEST999";
    private static final String TEST_STOCK_NAME = "测试股票";

    // ==================== 连接性测试 ====================

    @Test
    @Order(1)
    @DisplayName("测试MongoDB连接")
    void testMongoDBConnection() {
        log.info("========== 开始测试MongoDB连接 ==========");
        
        try {
            // 测试获取数据库信息
            String dbName = mongoTemplate.getDb().getName();
            log.info("MongoDB数据库名称: {}", dbName);
            assertEquals("stock_trading", dbName, "数据库名称应为stock_trading");
            
            // 测试获取集合列表
            var collections = mongoTemplate.getDb().listCollectionNames().first();
            log.info("MongoDB连接成功，集合样例: {}", collections);
            
            log.info("========== MongoDB连接测试通过 ==========");
        } catch (Exception e) {
            log.error("MongoDB连接失败", e);
            fail("MongoDB连接失败: " + e.getMessage());
        }
    }

    @Test
    @Order(2)
    @DisplayName("测试MySQL连接")
    void testMySQLConnection() {
        log.info("========== 开始测试MySQL连接 ==========");
        
        try {
            // 执行简单查询验证连接
            Integer result = jdbcTemplate.queryForObject(
                "SELECT 1", Integer.class);
            assertEquals(1, result, "MySQL查询应返回1");
            
            // 查询数据库版本
            String version = jdbcTemplate.queryForObject(
                "SELECT VERSION()", String.class);
            log.info("MySQL版本: {}", version);
            
            // 查询当前数据库
            String database = jdbcTemplate.queryForObject(
                "SELECT DATABASE()", String.class);
            log.info("当前数据库: {}", database);
            
            log.info("========== MySQL连接测试通过 ==========");
        } catch (Exception e) {
            log.error("MySQL连接失败", e);
            fail("MySQL连接失败: " + e.getMessage());
        }
    }

    @Test
    @Order(3)
    @DisplayName("测试Redis连接")
    void testRedisConnection() {
        log.info("========== 开始测试Redis连接 ==========");
        
        try {
            // 测试PING命令
            String pong = stringRedisTemplate.getConnectionFactory()
                .getConnection().ping();
            log.info("Redis PING响应: {}", pong);
            
            // 测试SET/GET操作
            String testKey = "test:connection";
            String testValue = "test_value_" + System.currentTimeMillis();
            
            stringRedisTemplate.opsForValue().set(testKey, testValue, 10, TimeUnit.SECONDS);
            String retrievedValue = stringRedisTemplate.opsForValue().get(testKey);
            
            assertEquals(testValue, retrievedValue, "Redis读写值应一致");
            log.info("Redis SET/GET测试通过: {} = {}", testKey, retrievedValue);
            
            log.info("========== Redis连接测试通过 ==========");
        } catch (Exception e) {
            log.error("Redis连接失败", e);
            fail("Redis连接失败: " + e.getMessage());
        }
    }

    // ==================== MongoDB StockInfo 读写测试 ====================

    @Test
    @Order(10)
    @DisplayName("测试StockInfo写入MongoDB")
    void testStockInfoWrite() {
        log.info("========== 开始测试StockInfo写入 ==========");
        
        // 创建测试数据
        StockInfo stockInfo = new StockInfo();
        stockInfo.setCode(TEST_STOCK_CODE);
        stockInfo.setName(TEST_STOCK_NAME);
        stockInfo.setListDate(LocalDate.now());
        stockInfo.setTotalShares(1000000000L);
        stockInfo.setCirculatingShares(500000000L);
        
        // 保存到MongoDB
        StockInfo saved = stockRepository.save(stockInfo);
        
        assertNotNull(saved.getId(), "保存后应有ID");
        assertEquals(TEST_STOCK_CODE, saved.getCode(), "股票代码应一致");
        assertEquals(TEST_STOCK_NAME, saved.getName(), "股票名称应一致");
        
        log.info("StockInfo写入成功: ID={}, Code={}", saved.getId(), saved.getCode());
        log.info("========== StockInfo写入测试通过 ==========");
    }

    @Test
    @Order(11)
    @DisplayName("测试StockInfo读取MongoDB")
    void testStockInfoRead() {
        log.info("========== 开始测试StockInfo读取 ==========");
        
        // 查询刚才写入的数据
        Optional<StockInfo> found = stockRepository.findByCode(TEST_STOCK_CODE);
        
        assertTrue(found.isPresent(), "应该能找到测试数据");
        assertEquals(TEST_STOCK_NAME, found.get().getName(), "名称应一致");
        
        // 测试查询所有
        List<StockInfo> allStocks = stockRepository.findAllByOrderByCodeAsc();
        assertFalse(allStocks.isEmpty(), "应该有数据");
        
        log.info("查询到股票总数: {}", allStocks.size());
        log.info("========== StockInfo读取测试通过 ==========");
    }

    @Test
    @Order(12)
    @DisplayName("测试StockInfo存在性检查")
    void testStockInfoExists() {
        log.info("========== 开始测试StockInfo存在性检查 ==========");
        
        boolean exists = stockRepository.existsByCode(TEST_STOCK_CODE);
        assertTrue(exists, "测试股票应该存在");
        
        boolean notExists = stockRepository.existsByCode("NOT_EXIST_CODE");
        assertFalse(notExists, "不存在的股票代码应返回false");
        
        log.info("========== StockInfo存在性检查测试通过 ==========");
    }

    // ==================== MongoDB StockPrice 读写测试 ====================

    @Test
    @Order(20)
    @DisplayName("测试StockPrice写入MongoDB")
    void testStockPriceWrite() {
        log.info("========== 开始测试StockPrice写入 ==========");
        
        // 创建测试价格数据
        StockPrice price = new StockPrice();
        price.setCode(TEST_STOCK_CODE);
        price.setName(TEST_STOCK_NAME);
        price.setDate(LocalDate.now());
        price.setOpenPrice(new BigDecimal("10.00"));
        price.setClosePrice(new BigDecimal("10.50"));
        price.setHighPrice(new BigDecimal("10.80"));
        price.setLowPrice(new BigDecimal("9.90"));
        price.setVolume(new BigDecimal("1000000"));
        price.setAmount(new BigDecimal("10500000"));
        
        // 保存到MongoDB
        StockPrice saved = priceRepository.save(price);
        
        assertNotNull(saved.getId(), "保存后应有ID");
        assertEquals(TEST_STOCK_CODE, saved.getCode(), "股票代码应一致");
        assertEquals(LocalDate.now(), saved.getDate(), "日期应一致");
        
        log.info("StockPrice写入成功: ID={}, Code={}, Date={}", 
            saved.getId(), saved.getCode(), saved.getDate());
        log.info("========== StockPrice写入测试通过 ==========");
    }

    @Test
    @Order(21)
    @DisplayName("测试StockPrice读取MongoDB")
    void testStockPriceRead() {
        log.info("========== 开始测试StockPrice读取 ==========");
        
        // 查询刚才写入的数据
        List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);
        
        assertFalse(prices.isEmpty(), "应该有价格数据");
        
        StockPrice latest = prices.get(prices.size() - 1);
        assertEquals(LocalDate.now(), latest.getDate(), "日期应为今天");
        
        // 测试按日期查询
        Optional<StockPrice> byDate = priceRepository.findByCodeAndDate(
            TEST_STOCK_CODE, LocalDate.now());
        assertTrue(byDate.isPresent(), "应该找到今天的记录");
        
        log.info("查询到价格数据数量: {}", prices.size());
        log.info("========== StockPrice读取测试通过 ==========");
    }

    @Test
    @Order(22)
    @DisplayName("测试StockPrice日期范围查询")
    void testStockPriceDateRangeQuery() {
        log.info("========== 开始测试StockPrice日期范围查询 ==========");
        
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();
        
        List<StockPrice> prices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(
            TEST_STOCK_CODE, startDate, endDate);
        
        assertNotNull(prices, "日期范围查询结果不应为null");
        log.info("日期范围查询结果数量: {}", prices.size());
        
        log.info("========== StockPrice日期范围查询测试通过 ==========");
    }

    // ==================== MongoDB StockNews 读写测试 ====================

    @Test
    @Order(30)
    @DisplayName("测试StockNews写入MongoDB")
    void testStockNewsWrite() {
        log.info("========== 开始测试StockNews写入 ==========");
        
        StockNews news = new StockNews();
        news.setTitle("测试新闻标题");
        news.setContent("这是一条测试新闻内容");
        news.setStockCode(TEST_STOCK_CODE);
        news.setSource("测试来源");
        news.setPublishTime(LocalDateTime.now());
        news.setCreateTime(LocalDateTime.now());
        
        StockNews saved = newsRepository.save(news);
        
        assertNotNull(saved.getId(), "保存后应有ID");
        assertEquals(TEST_STOCK_CODE, saved.getStockCode(), "股票代码应一致");
        
        log.info("StockNews写入成功: ID={}", saved.getId());
        log.info("========== StockNews写入测试通过 ==========");
    }

    @Test
    @Order(31)
    @DisplayName("测试StockNews读取MongoDB")
    void testStockNewsRead() {
        log.info("========== 开始测试StockNews读取 ==========");
        
        List<StockNews> newsList = newsRepository.findByStockCodeOrderByPublishTimeDesc(
            TEST_STOCK_CODE);
        
        assertFalse(newsList.isEmpty(), "应该有新闻数据");
        
        log.info("查询到新闻数量: {}", newsList.size());
        log.info("========== StockNews读取测试通过 ==========");
    }

    // ==================== MySQL 表操作测试 ====================

    @Test
    @Order(40)
    @DisplayName("测试MySQL表操作")
    void testMySQLTableOperations() {
        log.info("========== 开始测试MySQL表操作 ==========");
        
        try {
            // 创建测试表
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS test_table (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "name VARCHAR(100), " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );
            log.info("创建测试表成功");
            
            // 插入数据
            int inserted = jdbcTemplate.update(
                "INSERT INTO test_table (name) VALUES (?)", "测试数据");
            assertEquals(1, inserted, "应插入1条数据");
            log.info("插入测试数据成功");
            
            // 查询数据
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_table", Integer.class);
            assertTrue(count > 0, "应该有数据");
            log.info("查询测试数据成功, 数量: {}", count);
            
            // 删除数据
            jdbcTemplate.update("DELETE FROM test_table WHERE name = ?", "测试数据");
            log.info("删除测试数据成功");
            
            // 删除表
            jdbcTemplate.execute("DROP TABLE IF EXISTS test_table");
            log.info("删除测试表成功");
            
            log.info("========== MySQL表操作测试通过 ==========");
        } catch (Exception e) {
            log.error("MySQL表操作失败", e);
            fail("MySQL表操作失败: " + e.getMessage());
        }
    }

    // ==================== Redis 复杂操作测试 ====================

    @Test
    @Order(50)
    @DisplayName("测试Redis复杂操作")
    void testRedisComplexOperations() {
        log.info("========== 开始测试Redis复杂操作 ==========");
        
        String key = "stock:info:" + TEST_STOCK_CODE;
        
        try {
            // 设置JSON格式数据
            String jsonValue = String.format(
                "{\"code\":\"%s\",\"name\":\"%s\",\"price\":10.5}", 
                TEST_STOCK_CODE, TEST_STOCK_NAME);
            
            stringRedisTemplate.opsForValue().set(key, jsonValue, 1, TimeUnit.MINUTES);
            log.info("Redis写入JSON数据成功");
            
            // 读取数据
            String retrieved = stringRedisTemplate.opsForValue().get(key);
            assertNotNull(retrieved, "应该能读取数据");
            assertTrue(retrieved.contains(TEST_STOCK_CODE), "数据应包含股票代码");
            log.info("Redis读取数据: {}", retrieved);
            
            // 测试TTL
            Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            assertTrue(ttl > 0, "TTL应大于0");
            log.info("TTL剩余秒数: {}", ttl);
            
            // 删除数据
            Boolean deleted = stringRedisTemplate.delete(key);
            assertTrue(deleted, "删除应成功");
            log.info("Redis删除数据成功");
            
            log.info("========== Redis复杂操作测试通过 ==========");
        } catch (Exception e) {
            log.error("Redis操作失败", e);
            fail("Redis操作失败: " + e.getMessage());
        }
    }

    // ==================== 清理测试数据 ====================

    @Test
    @Order(100)
    @DisplayName("清理测试数据")
    void cleanupTestData() {
        log.info("========== 开始清理测试数据 ==========");
        
        // 清理MongoDB测试数据
        stockRepository.deleteByCode(TEST_STOCK_CODE);
        priceRepository.deleteByCode(TEST_STOCK_CODE);
        newsRepository.deleteByStockCode(TEST_STOCK_CODE);
        
        log.info("测试数据清理完成");
        log.info("========== 清理测试数据完成 ==========");
    }
}