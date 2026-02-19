package online.mwang.stockTrading.modules.datacollection;

import com.alibaba.fastjson2.JSON;
import online.mwang.stockTrading.modules.datacollection.client.AKToolsClient;
import online.mwang.stockTrading.modules.datacollection.dto.KLineDTO;
import online.mwang.stockTrading.modules.datacollection.dto.QuoteDTO;
import online.mwang.stockTrading.modules.datacollection.dto.StockInfoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AKToolsClient 单元测试类
 * 测试用例 TC-001-001 至 TC-001-005
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AKTools API客户端测试")
class AKToolsClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AKToolsClient akToolsClient;

    private static final String BASE_URL = "https://api.aktools.com";
    private static final int MAX_RETRY = 3;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(akToolsClient, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(akToolsClient, "timeout", 30000);
        ReflectionTestUtils.setField(akToolsClient, "maxRetryAttempts", MAX_RETRY);
    }

    @Nested
    @DisplayName("TC-001-001: 测试获取股票列表")
    class GetStockListTests {

        @Test
        @DisplayName("TC-001-001: 正常获取股票列表")
        void testGetStockList_Success() {
            // Given: 模拟API返回正常的股票列表数据
            String mockResponse = """
                [
                    {"code": "000001", "name": "平安银行"},
                    {"code": "000002", "name": "万科A"},
                    {"code": "600000", "name": "浦发银行"},
                    {"code": "600519", "name": "贵州茅台"},
                    {"code": "000003", "name": "ST国农"}
                ]
                """;

            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok(mockResponse));

            // When: 调用获取股票列表方法
            List<StockInfoDTO> stockList = akToolsClient.getStockList();

            // Then: 验证返回结果
            assertNotNull(stockList, "返回列表不应为空");
            assertEquals(5, stockList.size(), "应返回5条股票记录");

            // 验证第一条数据字段
            StockInfoDTO firstStock = stockList.get(0);
            assertEquals("000001", firstStock.getStockCode(), "股票代码应匹配");
            assertEquals("平安银行", firstStock.getStockName(), "股票名称应匹配");
            assertEquals("SZ", firstStock.getMarket(), "深圳股票市场应为SZ");

            // 验证ST股票识别
            StockInfoDTO stStock = stockList.stream()
                    .filter(s -> "000003".equals(s.getStockCode()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(stStock);
            assertTrue(stStock.getIsSt(), "ST股票应被正确识别");

            verify(restTemplate, times(1)).exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class));
        }

        @Test
        @DisplayName("TC-001-002: 网络异常时返回空列表")
        void testGetStockList_NetworkError() {
            // Given: 模拟网络异常
            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(),
                    eq(String.class)
            )).thenThrow(new RuntimeException("Network timeout"));

            // When: 调用获取股票列表方法
            List<StockInfoDTO> stockList = akToolsClient.getStockList();

            // Then: 验证返回空列表而不是抛异常
            assertNotNull(stockList, "即使网络异常也应返回非空对象");
            assertTrue(stockList.isEmpty(), "网络异常时应返回空列表");
        }

        @Test
        @DisplayName("TC-001-002: 网络超时后重试")
        void testGetStockList_RetryOnTimeout() {
            // Given: 模拟前两次失败，第三次成功
            String mockResponse = "[{\"code\": \"000001\", \"name\": \"平安银行\"}]";

            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(),
                    eq(String.class)
            ))
                    .thenThrow(new RuntimeException("Timeout 1"))
                    .thenThrow(new RuntimeException("Timeout 2"))
                    .thenReturn(ResponseEntity.ok(mockResponse));

            // When: 调用获取股票列表方法
            List<StockInfoDTO> stockList = akToolsClient.getStockList();

            // Then: 验证最终成功获取数据
            assertNotNull(stockList);
            assertFalse(stockList.isEmpty(), "重试后应成功获取数据");

            // 验证重试次数
            verify(restTemplate, times(3)).exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class));
        }
    }

    @Nested
    @DisplayName("TC-001-003: 测试获取K线数据")
    class GetKLineTests {

        @Test
        @DisplayName("TC-001-003: 正常获取K线数据")
        void testGetKLine_Success() {
            // Given: 模拟API返回K线数据
            String mockResponse = """
                [
                    {"日期": "2024-01-15", "开盘": 10.5, "最高": 11.0, "最低": 10.3, "收盘": 10.8, "成交量": 1000000, "成交额": 10800000, "涨跌幅": 2.5, "换手率": 1.5},
                    {"日期": "2024-01-16", "开盘": 10.8, "最高": 11.2, "最低": 10.6, "收盘": 11.0, "成交量": 1200000, "成交额": 13200000, "涨跌幅": 1.85, "换手率": 1.8}
                ]
                """;

            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok(mockResponse));

            // When: 调用获取K线数据方法
            String stockCode = "000001";
            LocalDate startDate = LocalDate.of(2024, 1, 15);
            LocalDate endDate = LocalDate.of(2024, 1, 16);
            List<KLineDTO> kLineData = akToolsClient.getKLine(stockCode, startDate, endDate);

            // Then: 验证返回结果
            assertNotNull(kLineData, "返回数据不应为空");
            assertEquals(2, kLineData.size(), "应返回2条K线记录");

            // 验证数据包含OHLCV字段
            KLineDTO firstKLine = kLineData.get(0);
            assertNotNull(firstKLine.getTradeDate(), "日期不应为空");
            assertNotNull(firstKLine.getOpen(), "开盘价不应为空");
            assertNotNull(firstKLine.getHigh(), "最高价不应为空");
            assertNotNull(firstKLine.getLow(), "最低价不应为空");
            assertNotNull(firstKLine.getClose(), "收盘价不应为空");
            assertNotNull(firstKLine.getVolume(), "成交量不应为空");

            // 验证日期范围
            assertTrue(firstKLine.getTradeDate().compareTo(startDate) >= 0 ||
                    firstKLine.getTradeDate().compareTo(endDate) <= 0);
        }

        @Test
        @DisplayName("TC-001-003: 空响应返回空列表")
        void testGetKLine_EmptyResponse() {
            // Given: 模拟API返回空数据
            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok("[]"));

            // When: 调用获取K线数据方法
            List<KLineDTO> kLineData = akToolsClient.getKLine("999999", LocalDate.now().minusDays(10), LocalDate.now());

            // Then: 验证返回空列表
            assertNotNull(kLineData);
            assertTrue(kLineData.isEmpty());
        }
    }

    @Nested
    @DisplayName("TC-001-004: 测试获取实时价格")
    class GetQuoteTests {

        @Test
        @DisplayName("TC-001-004: 正常获取实时报价")
        void testGetQuote_Success() {
            // Given: 模拟API返回实时行情
            String mockResponse = """
                [
                    {"代码": "000001", "名称": "平安银行", "最新价": 10.85, "涨跌额": 0.15, "涨跌幅": 1.40, "今开": 10.70, "最高": 10.90, "最低": 10.65, "昨收": 10.70, "成交量": 50000000, "成交额": 542000000}
                ]
                """;

            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok(mockResponse));

            // When: 调用获取实时价格方法
            QuoteDTO quote = akToolsClient.getQuote("000001");

            // Then: 验证返回结果
            assertNotNull(quote, "返回数据不应为空");
            assertEquals("000001", quote.getStockCode(), "股票代码应匹配");
            assertEquals("平安银行", quote.getStockName(), "股票名称应匹配");
            assertTrue(quote.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0, "价格应大于0");
            assertNotNull(quote.getChange(), "涨跌额不应为空");
            assertNotNull(quote.getChangePercent(), "涨跌幅不应为空");
        }

        @Test
        @DisplayName("TC-001-004: 无匹配股票返回null")
        void testGetQuote_NotFound() {
            // Given: 模拟API返回数据但不包含目标股票
            String mockResponse = """
                [
                    {"代码": "000002", "名称": "万科A", "最新价": 15.20}
                ]
                """;

            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok(mockResponse));

            // When: 查询不存在的股票
            QuoteDTO quote = akToolsClient.getQuote("999999");

            // Then: 验证返回null
            assertNull(quote, "未找到股票应返回null");
        }
    }

    @Nested
    @DisplayName("TC-001-005: 测试重试机制")
    class RetryTests {

        @Test
        @DisplayName("TC-001-005: 重试机制-最终成功")
        void testRetry_SuccessOnThirdAttempt() {
            // Given: 模拟前两次失败，第三次成功
            String mockResponse = "[{\"code\": \"000001\", \"name\": \"平安银行\"}]";

            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(),
                    eq(String.class)
            ))
                    .thenThrow(new RuntimeException("First failure"))
                    .thenThrow(new RuntimeException("Second failure"))
                    .thenReturn(ResponseEntity.ok(mockResponse));

            // When: 调用方法
            List<StockInfoDTO> result = akToolsClient.getStockList();

            // Then: 验证最终成功返回结果
            assertNotNull(result);
            assertFalse(result.isEmpty());

            // 验证重试次数
            verify(restTemplate, times(3)).exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class));
        }

        @Test
        @DisplayName("TC-001-005: 重试机制-全部失败")
        void testRetry_AllAttemptsFailed() {
            // Given: 模拟所有重试都失败
            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(),
                    eq(String.class)
            )).thenThrow(new RuntimeException("Persistent failure"));

            // When: 调用方法
            List<StockInfoDTO> result = akToolsClient.getStockList();

            // Then: 验证返回空列表（通过recover方法）
            assertNotNull(result);
            assertTrue(result.isEmpty());

            // 验证达到最大重试次数
            verify(restTemplate, times(MAX_RETRY)).exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class));
        }
    }

    @Nested
    @DisplayName("批量获取行情测试")
    class GetQuotesTests {

        @Test
        @DisplayName("批量获取实时行情")
        void testGetQuotes_Success() {
            // Given: 模拟API返回多只股票行情
            String mockResponse = """
                [
                    {"代码": "000001", "名称": "平安银行", "最新价": 10.85, "涨跌额": 0.15, "涨跌幅": 1.40},
                    {"代码": "000002", "名称": "万科A", "最新价": 15.20, "涨跌额": -0.10, "涨跌幅": -0.65},
                    {"代码": "600519", "名称": "贵州茅台", "最新价": 1850.00, "涨跌额": 20.00, "涨跌幅": 1.09}
                ]
                """;

            when(restTemplate.exchange(
                    any(String.class),
                    eq(HttpMethod.GET),
                    any(),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok(mockResponse));

            // When: 批量获取行情
            List<String> stockCodes = List.of("000001", "000002");
            List<QuoteDTO> quotes = akToolsClient.getQuotes(stockCodes);

            // Then: 验证返回结果
            assertNotNull(quotes);
            assertEquals(2, quotes.size(), "应返回2只股票的行情");
            
            // 验证只返回请求的股票
            assertTrue(quotes.stream().anyMatch(q -> "000001".equals(q.getStockCode())));
            assertTrue(quotes.stream().anyMatch(q -> "000002".equals(q.getStockCode())));
        }
    }
}
