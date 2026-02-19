package online.mwang.stockTrading.modules.datacollection;

import online.mwang.stockTrading.modules.datacollection.client.AKToolsClient;
import online.mwang.stockTrading.modules.datacollection.dto.KLineDTO;
import online.mwang.stockTrading.modules.datacollection.dto.QuoteDTO;
import online.mwang.stockTrading.modules.datacollection.dto.StockInfoDTO;
import online.mwang.stockTrading.modules.datacollection.entity.StockInfo;
import online.mwang.stockTrading.modules.datacollection.entity.StockPrices;
import online.mwang.stockTrading.modules.datacollection.mapper.StockInfoMapper;
import online.mwang.stockTrading.modules.datacollection.repository.StockPricesRepository;
import online.mwang.stockTrading.modules.datacollection.service.impl.StockDataServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StockDataServiceImpl 单元测试类
 * 测试用例 TC-001-006 至 TC-001-014
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("股票数据服务测试")
class StockDataServiceImplUnitTest {

    @Mock
    private StockInfoMapper stockInfoMapper;

    @Mock
    private StockPricesRepository stockPricesRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private AKToolsClient akToolsClient;

    @InjectMocks
    private StockDataServiceImpl stockDataService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("TC-001-006: 测试获取可交易股票列表")
    class FetchStockListTests {

        @Test
        @DisplayName("TC-001-006: 获取可交易股票列表-过滤ST股票")
        void testFetchStockList_FilterStStocks() {
            // Given: 模拟API返回包含ST股票的数据
            List<StockInfoDTO> mockDtoList = Arrays.asList(
                    createStockInfoDTO("000001", "平安银行", false, true),
                    createStockInfoDTO("000002", "万科A", false, true),
                    createStockInfoDTO("000003", "ST国农", true, false),
                    createStockInfoDTO("000004", "*ST康美", true, false),
                    createStockInfoDTO("600519", "贵州茅台", false, true)
            );

            when(akToolsClient.getStockList()).thenReturn(mockDtoList);

            // When: 调用获取股票列表方法
            List<StockInfo> stockList = stockDataService.fetchStockList();

            // Then: 验证返回结果
            assertNotNull(stockList);
            assertEquals(3, stockList.size(), "应过滤掉2只ST股票");

            // 验证不包含ST股票
            boolean hasStStock = stockList.stream()
                    .anyMatch(s -> Boolean.TRUE.equals(s.getIsSt()));
            assertFalse(hasStStock, "不应包含ST股票");

            // 验证所有股票都是可交易的
            boolean allTradable = stockList.stream()
                    .allMatch(s -> Boolean.TRUE.equals(s.getIsTradable()));
            assertTrue(allTradable, "所有股票应为可交易状态");
        }

        @Test
        @DisplayName("TC-001-008: 数据库无数据时从API获取")
        void testFetchStockList_FromApi() {
            // Given: 模拟API返回数据
            List<StockInfoDTO> mockDtoList = Arrays.asList(
                    createStockInfoDTO("000001", "平安银行", false, true),
                    createStockInfoDTO("000002", "万科A", false, true)
            );

            when(akToolsClient.getStockList()).thenReturn(mockDtoList);

            // When: 调用获取股票列表
            List<StockInfo> stockList = stockDataService.fetchStockList();

            // Then: 验证调用了API
            verify(akToolsClient, times(1)).getStockList();
            assertNotNull(stockList);
            assertEquals(2, stockList.size());
        }

        @Test
        @DisplayName("TC-001-002: API返回空数据")
        void testFetchStockList_EmptyResponse() {
            // Given: 模拟API返回空数据
            when(akToolsClient.getStockList()).thenReturn(Collections.emptyList());

            // When: 调用获取股票列表
            List<StockInfo> stockList = stockDataService.fetchStockList();

            // Then: 验证返回空列表
            assertNotNull(stockList);
            assertTrue(stockList.isEmpty());
        }
    }

    @Nested
    @DisplayName("TC-001-007/008: 测试获取历史数据")
    class FetchHistoricalDataTests {

        @Test
        @DisplayName("TC-001-007: 从数据库获取历史数据")
        void testFetchHistoricalData_FromDatabase() {
            // Given: 数据库有历史数据
            String stockCode = "000001";
            List<StockPrices> mockLocalData = createMockStockPrices(stockCode, 30);

            when(stockPricesRepository.findByCodeAndDateBetween(anyString(), anyString(), anyString()))
                    .thenReturn(mockLocalData);

            // When: 调用获取历史数据方法
            List<StockPrices> prices = stockDataService.fetchHistoricalData(stockCode, 30);

            // Then: 验证从数据库读取
            assertNotNull(prices);
            assertEquals(30, prices.size(), "应返回30条记录");

            // 验证没有调用API
            verify(akToolsClient, never()).getKLine(anyString(), any(), any());
        }

        @Test
        @DisplayName("TC-001-008: 数据库无数据时从API获取")
        void testFetchHistoricalData_FromApi() {
            // Given: 数据库无数据
            String stockCode = "000001";
            when(stockPricesRepository.findByCodeAndDateBetween(anyString(), anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            // 模拟API返回数据
            List<KLineDTO> mockKLineData = createMockKLineData(30);
            when(akToolsClient.getKLine(anyString(), any(), any())).thenReturn(mockKLineData);

            // When: 调用获取历史数据方法
            List<StockPrices> prices = stockDataService.fetchHistoricalData(stockCode, 30);

            // Then: 验证调用了API
            verify(akToolsClient, times(1)).getKLine(eq(stockCode), any(), any());
            assertNotNull(prices);
            assertEquals(30, prices.size());
        }

        @Test
        @DisplayName("TC-001-008: API数据写入MongoDB")
        void testFetchHistoricalData_SaveToMongo() {
            // Given: 数据库无数据
            String stockCode = "000001";
            when(stockPricesRepository.findByCodeAndDateBetween(anyString(), anyString(), anyString()))
                    .thenReturn(Collections.emptyList());
            when(stockPricesRepository.findByCodeAndDate(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // 模拟API返回数据
            List<KLineDTO> mockKLineData = createMockKLineData(5);
            when(akToolsClient.getKLine(anyString(), any(), any())).thenReturn(mockKLineData);

            // When: 调用获取历史数据方法
            stockDataService.fetchHistoricalData(stockCode, 5);

            // Then: 验证数据保存到MongoDB
            verify(stockPricesRepository, times(5)).save(any(StockPrices.class));
        }
    }

    @Nested
    @DisplayName("TC-001-009/010: 测试获取实时价格")
    class FetchRealTimePriceTests {

        @Test
        @DisplayName("TC-001-009: 缓存命中")
        void testFetchRealTimePrice_CacheHit() {
            // Given: Redis有缓存
            String stockCode = "000001";
            Double cachedPrice = 10.85;

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(cachedPrice);

            // When: 调用获取实时价格方法
            Double price = stockDataService.fetchRealTimePrice(stockCode);

            // Then: 验证从缓存读取
            assertNotNull(price);
            assertEquals(cachedPrice, price);

            // 验证没有调用API
            verify(akToolsClient, never()).getQuote(anyString());
        }

        @Test
        @DisplayName("TC-001-010: 缓存未命中-调用API并缓存")
        void testFetchRealTimePrice_CacheMiss() {
            // Given: Redis无缓存
            String stockCode = "000001";
            Double apiPrice = 10.90;

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);

            // 模拟API返回
            QuoteDTO mockQuote = new QuoteDTO();
            mockQuote.setCurrentPrice(BigDecimal.valueOf(apiPrice));
            when(akToolsClient.getQuote(stockCode)).thenReturn(mockQuote);

            // When: 调用获取实时价格方法
            Double price = stockDataService.fetchRealTimePrice(stockCode);

            // Then: 验证调用了API
            verify(akToolsClient, times(1)).getQuote(stockCode);
            assertNotNull(price);
            assertEquals(apiPrice, price);

            // 验证写入了缓存
            verify(valueOperations, times(1)).set(anyString(), eq(apiPrice), anyLong(), any());
        }

        @Test
        @DisplayName("非交易时间返回null")
        void testFetchRealTimePrice_OutsideTradingHours() {
            // Given: 缓存无数据，API返回null
            String stockCode = "000001";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(akToolsClient.getQuote(stockCode)).thenReturn(null);

            // When: 调用获取实时价格方法
            Double price = stockDataService.fetchRealTimePrice(stockCode);

            // Then: 验证返回null
            assertNull(price);
        }
    }

    @Nested
    @DisplayName("TC-001-011: 测试同步单只股票历史")
    class SyncStockHistoryTests {

        @Test
        @DisplayName("TC-001-011: 同步单只股票历史数据")
        void testSyncStockHistory_Success() {
            // Given: 模拟API返回数据
            String stockCode = "000001";
            int days = 60;

            when(stockPricesRepository.findByCodeAndDateBetween(anyString(), anyString(), anyString()))
                    .thenReturn(Collections.emptyList());
            when(stockPricesRepository.findByCodeAndDate(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            List<KLineDTO> mockKLineData = createMockKLineData(60);
            when(akToolsClient.getKLine(anyString(), any(), any())).thenReturn(mockKLineData);

            // When: 调用同步方法
            assertDoesNotThrow(() -> stockDataService.syncStockHistory(stockCode, days));

            // Then: 验证调用了API
            verify(akToolsClient, times(1)).getKLine(eq(stockCode), any(), any());
        }
    }

    @Nested
    @DisplayName("TC-001-012: 测试全量同步")
    class SyncAllStocksTests {

        @Test
        @DisplayName("TC-001-012: 全量同步流程")
        void testSyncAllStocks_Success() {
            // Given: 模拟数据
            List<StockInfoDTO> mockDtoList = Arrays.asList(
                    createStockInfoDTO("000001", "平安银行", false, true),
                    createStockInfoDTO("000002", "万科A", false, true)
            );
            when(akToolsClient.getStockList()).thenReturn(mockDtoList);
            when(stockInfoMapper.getByCode(anyString())).thenReturn(null);
            when(stockInfoMapper.insert(any(StockInfo.class))).thenReturn(1);
            when(stockPricesRepository.findByCodeAndDateBetween(anyString(), anyString(), anyString()))
                    .thenReturn(Collections.emptyList());
            when(stockPricesRepository.findByCodeAndDate(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(akToolsClient.getKLine(anyString(), any(), any()))
                    .thenReturn(createMockKLineData(10));

            // When: 调用全量同步方法
            assertDoesNotThrow(() -> stockDataService.syncAllStocks());

            // Then: 验证同步流程
            verify(akToolsClient, times(1)).getStockList();
            verify(stockInfoMapper, atLeastOnce()).insert(any(StockInfo.class));
        }
    }

    @Nested
    @DisplayName("TC-001-013: 测试重复数据处理")
    class DuplicateDataTests {

        @Test
        @DisplayName("TC-001-013: 重复数据更新而非插入")
        void testDuplicateData_Update() {
            // Given: 数据已存在
            String stockCode = "000001";
            StockPrices existingPrice = new StockPrices();
            existingPrice.setId("existing-id");
            existingPrice.setCode(stockCode);
            existingPrice.setDate("2024-01-15");
            existingPrice.setPrice1(10.0);

            when(stockPricesRepository.findByCodeAndDateBetween(anyString(), anyString(), anyString()))
                    .thenReturn(Collections.emptyList());
            when(stockPricesRepository.findByCodeAndDate(anyString(), anyString()))
                    .thenReturn(Optional.of(existingPrice));

            List<KLineDTO> mockKLineData = createMockKLineData(1);
            when(akToolsClient.getKLine(anyString(), any(), any())).thenReturn(mockKLineData);

            // When: 同步相同数据
            stockDataService.fetchHistoricalData(stockCode, 1);

            // Then: 验证更新操作
            verify(stockPricesRepository, times(1)).save(any(StockPrices.class));
        }
    }

    @Nested
    @DisplayName("TC-001-014: 测试异常处理")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("TC-001-014: 异常处理-不崩溃")
        void testExceptionHandling_DoesNotCrash() {
            // Given: 模拟数据库异常
            when(akToolsClient.getStockList()).thenThrow(new RuntimeException("Database connection failed"));

            // When: 调用方法
            List<StockInfo> result = stockDataService.fetchStockList();

            // Then: 验证不崩溃，返回空列表
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== Helper Methods ====================

    private StockInfoDTO createStockInfoDTO(String code, String name, boolean isSt, boolean isTradable) {
        StockInfoDTO dto = new StockInfoDTO();
        dto.setStockCode(code);
        dto.setStockName(name);
        dto.setMarket(code.startsWith("6") ? "SH" : "SZ");
        dto.setIsSt(isSt);
        dto.setIsTradable(isTradable);
        return dto;
    }

    private List<StockPrices> createMockStockPrices(String code, int count) {
        List<StockPrices> prices = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(count);
        for (int i = 0; i < count; i++) {
            StockPrices price = new StockPrices();
            price.setCode(code);
            price.setDate(baseDate.plusDays(i).toString());
            price.setPrice1(10.0 + i * 0.1);
            price.setPrice2(10.5 + i * 0.1);
            price.setPrice3(9.8 + i * 0.1);
            price.setPrice4(10.2 + i * 0.1);
            prices.add(price);
        }
        return prices;
    }

    private List<KLineDTO> createMockKLineData(int count) {
        List<KLineDTO> klines = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(count);
        for (int i = 0; i < count; i++) {
            KLineDTO kline = new KLineDTO();
            kline.setTradeDate(baseDate.plusDays(i));
            kline.setOpen(BigDecimal.valueOf(10.0 + i * 0.1));
            kline.setHigh(BigDecimal.valueOf(10.5 + i * 0.1));
            kline.setLow(BigDecimal.valueOf(9.8 + i * 0.1));
            kline.setClose(BigDecimal.valueOf(10.2 + i * 0.1));
            kline.setVolume(1000000L + i * 10000);
            kline.setAmount(BigDecimal.valueOf(10000000 + i * 100000));
            kline.setChangePct(BigDecimal.valueOf(1.5));
            kline.setTurnoverRate(BigDecimal.valueOf(2.5));
            klines.add(kline);
        }
        return klines;
    }
}
