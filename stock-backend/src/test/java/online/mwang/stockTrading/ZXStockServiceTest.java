package online.mwang.stockTrading;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.services.impl.ZXStockServiceImpl;
import online.mwang.stockTrading.web.bean.dto.OrderStatus;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.services.StockInfoService;
import online.mwang.stockTrading.services.TradingRecordService;
import online.mwang.stockTrading.repositories.AccountInfoRepository;
import online.mwang.stockTrading.repositories.ModelInfoRepository;
import online.mwang.stockTrading.repositories.StockInfoRepository;
import online.mwang.stockTrading.utils.OcrUtils;
import online.mwang.stockTrading.utils.RequestUtils;
import online.mwang.stockTrading.utils.SleepUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 交易执行模块测试 - ZXStockServiceImpl
 * 模块八：交易执行
 *
 * 验收标准：
 * 1. 买入执行 - 正确提交买入委托
 * 2. 卖出执行 - 正确提交卖出委托
 * 3. 持仓查询 - 正确返回持仓信息
 * 4. 账户资金查询 - 正确返回资金信息
 * 5. 订单状态查询 - 正确返回订单状态
 * 6. 撤单功能 - 正确撤销未成交订单
 */
@DisplayName("ZXStockServiceImpl - 交易执行模块测试")
@ExtendWith(MockitoExtension.class)
public class ZXStockServiceTest {

    @Mock
    private RequestUtils requestUtils;

    @Mock
    private OcrUtils ocrUtils;

    @Mock
    private StockInfoService stockInfoService;

    @Mock
    private TradingRecordService tradingRecordService;

    @Mock
    private AccountInfoRepository accountInfoRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private StockInfoRepository stockInfoRepository;

    @Mock
    private ModelInfoRepository strategyMapper;

    @Mock
    private SleepUtils sleepUtils;

    private ZXStockServiceImpl zxStockService;

    @BeforeEach
    void setUp() {
        zxStockService = new ZXStockServiceImpl(
            requestUtils,
            ocrUtils,
            stockInfoService,
            tradingRecordService,
            accountInfoRepository,
            redisTemplate,
            mongoTemplate,
            stockInfoRepository,
            strategyMapper,
            sleepUtils
        );
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== Token管理测试 ====================

    @Test
    @DisplayName("Token获取 - 从Redis获取")
    void testGetToken_FromRedis() {
        when(valueOperations.get("requestToken")).thenReturn("test_token_12345");
        
        String token = zxStockService.getToken();
        
        assertEquals("test_token_12345", token);
        verify(valueOperations).get("requestToken");
    }

    @Test
    @DisplayName("Token设置 - 写入Redis")
    void testSetToken_ToRedis() {
        String token = "new_token_67890";
        
        zxStockService.setToken(token);
        
        verify(valueOperations).set("requestToken", token);
    }

    @Test
    @DisplayName("Token设置 - 空值不写入")
    void testSetToken_NullValue() {
        zxStockService.setToken(null);
        
        verify(valueOperations, never()).set(anyString(), any());
    }

    // ==================== FR-001: 买入委托测试 ====================

    @Test
    @DisplayName("买入委托 - 参数构建正确")
    void testBuySale_BuyOrder() {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONObject mockResult = new JSONObject();
        mockResult.put("ERRORNO", "0");
        when(requestUtils.request(any())).thenReturn(mockResult);
        
        JSONObject result = zxStockService.buySale("买入", "600519", 10.0, 100.0);
        
        assertNotNull(result);
        assertEquals("0", result.getString("ERRORNO"));
    }

    @Test
    @DisplayName("买入委托 - 限价委托参数验证")
    void testBuySale_LimitOrder() {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONObject mockResult = new JSONObject();
        mockResult.put("ERRORNO", "0");
        when(requestUtils.request(any())).thenReturn(mockResult);
        
        zxStockService.buySale("买入", "600519", 10.0, 100.0);
        
        verify(requestUtils).request(argThat(params -> {
            java.util.HashMap<String, Object> map = (java.util.HashMap<String, Object>) params;
            return "买入".equals(map.get("Direction"))
                && "600519".equals(map.get("StockCode"))
                && 10.0 == ((Number) map.get("Price")).doubleValue()
                && 100.0 == ((Number) map.get("Volume")).doubleValue()
                && 110 == ((Number) map.get("action")).intValue();
        }));
    }

    // ==================== FR-002: 卖出委托测试 ====================

    @Test
    @DisplayName("卖出委托 - 参数构建正确")
    void testBuySale_SellOrder() {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONObject mockResult = new JSONObject();
        mockResult.put("ERRORNO", "0");
        when(requestUtils.request(any())).thenReturn(mockResult);
        
        JSONObject result = zxStockService.buySale("卖出", "600519", 10.0, 100.0);
        
        assertNotNull(result);
    }

    // ==================== FR-003: 账户资金查询测试 ====================

    @Test
    @DisplayName("账户资金查询 - 返回正确格式")
    void testGetAccountInfo_Format() {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONArray mockArray = new JSONArray();
        mockArray.add(0, "header");
        mockArray.add(1, "RMB|100000.00|50000.00|50000.00|100000.00|50000.00|0.00|880008900626|RMB|1");
        
        when(requestUtils.request2(any())).thenReturn(mockArray);
        
        AccountInfo accountInfo = zxStockService.getAccountInfo();
        
        assertNotNull(accountInfo);
        assertEquals(50000.0, accountInfo.getAvailableAmount());
        assertEquals(50000.0, accountInfo.getUsedAmount());
        assertEquals(100000.0, accountInfo.getTotalAmount());
    }

    @Test
    @DisplayName("账户资金查询 - 空结果处理")
    void testGetAccountInfo_EmptyResult() {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONArray mockArray = new JSONArray();
        when(requestUtils.request2(any())).thenReturn(mockArray);
        
        AccountInfo accountInfo = zxStockService.getAccountInfo();
        
        assertNull(accountInfo);
    }

    // ==================== FR-004: 持仓查询测试 ====================

    @Test
    @DisplayName("获取股票列表 - 返回数据列表")
    void testGetDataList_ReturnList() {
        JSONArray mockArray = new JSONArray();
        mockArray.add("[1.5,100.0,\"贵州茅台\",\"SH\",\"600519\"]");
        
        when(requestUtils.request3(any())).thenReturn(mockArray);
        
        List<StockInfo> stockInfos = zxStockService.getDataList();
        
        assertNotNull(stockInfos);
        assertEquals(1, stockInfos.size());
        assertEquals("600519", stockInfos.get(0).getCode());
    }

    // ==================== FR-005: 订单状态查询测试 ====================

    @Test
    @DisplayName("获取今日订单 - 返回订单列表")
    void testGetTodayOrder_ReturnList() {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONArray mockArray = new JSONArray();
        mockArray.add(0, "header");
        mockArray.add(1, "贵州茅台|买入|100|10.0|600519|ORDER123|已成交|2024-01-15|10:30:00");
        
        when(requestUtils.request2(any())).thenReturn(mockArray);
        
        List<OrderInfo> orders = zxStockService.getTodayOrder();
        
        assertNotNull(orders);
        assertEquals(1, orders.size());
    }

    @Test
    @DisplayName("获取历史订单 - 返回订单列表")
    void testGetHistoryOrder_ReturnList() {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONArray mockArray = new JSONArray();
        mockArray.add(0, "header");
        mockArray.add(1, "2024-01-10|ORDER456|600519|贵州茅台|买入|10.0|100");
        
        when(requestUtils.request2(any())).thenReturn(mockArray);
        
        List<OrderInfo> orders = zxStockService.getHistoryOrder("2024-01-01", "2024-01-31");
        
        assertNotNull(orders);
    }

    // ==================== FR-006: 撤单测试 ====================

    @Test
    @DisplayName("撤销订单 - 参数构建正确")
    void testCancelOrder_Params() {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONObject mockResult = new JSONObject();
        when(requestUtils.request(any())).thenReturn(mockResult);
        
        zxStockService.cancelOrder("ORDER123");
        
        verify(requestUtils).request(argThat(params -> {
            java.util.HashMap<String, Object> map = (java.util.HashMap<String, Object>) params;
            return "ORDER123".equals(map.get("ContactID"))
                && 111 == ((Number) map.get("action")).intValue();
        }));
    }

    @Test
    @DisplayName("撤销全部订单 - 返回数量")
    void testCancelAllOrder_ReturnCount() {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONArray mockArray = new JSONArray();
        mockArray.add(0, "header");
        mockArray.add(1, "贵州茅台|买入|100|10.0|600519|ORDER123|已报|2024-01-15|10:30:00");
        
        when(requestUtils.request2(any())).thenReturn(mockArray);
        
        Integer count = zxStockService.cancelAllOrder();
        
        assertTrue(count >= 0);
    }

    // ==================== 等待订单成功测试 ====================

    @Test
    @DisplayName("等待订单成功 - 已成状态返回true")
    void testWaitSuccess_Filled() {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONArray mockArray = new JSONArray();
        mockArray.add(0, "header");
        mockArray.add(1, "贵州茅台|买入|100|10.0|600519|ORDER123|已成|2024-01-15|10:30:00");
        
        when(requestUtils.request2(any())).thenReturn(mockArray);
        
        boolean result = zxStockService.waitSuccess("ORDER123");
        
        assertTrue(result);
    }

    @Test
    @DisplayName("等待订单成功 - 已撤状态返回false")
    void testWaitSuccess_Cancelled() {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONArray mockArray = new JSONArray();
        mockArray.add(0, "header");
        mockArray.add(1, "贵州茅台|买入|100|10.0|600519|ORDER123|已撤|2024-01-15|10:30:00");
        
        when(requestUtils.request2(any())).thenReturn(mockArray);
        
        boolean result = zxStockService.waitSuccess("ORDER123");
        
        assertFalse(result);
    }

    @Test
    @DisplayName("等待订单成功 - 废单状态返回false")
    void testWaitSuccess_Rejected() {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONArray mockArray = new JSONArray();
        mockArray.add(0, "header");
        mockArray.add(1, "贵州茅台|买入|100|10.0|600519|ORDER123|废单|2024-01-15|10:30:00");
        
        when(requestUtils.request2(any())).thenReturn(mockArray);
        
        boolean result = zxStockService.waitSuccess("ORDER123");
        
        assertFalse(result);
    }

    // ==================== 价格查询测试 ====================

    @Test
    @DisplayName("获取当前价格 - 返回价格")
    void testGetNowPrice_ReturnPrice() {
        JSONObject mockResult = new JSONObject();
        mockResult.put("PRICE", 10.5);
        
        when(requestUtils.request(any())).thenReturn(mockResult);
        
        Double price = zxStockService.getNowPrice("600519");
        
        assertEquals(10.5, price);
    }

    // ==================== 历史价格查询测试 ====================

    @Test
    @DisplayName("获取历史价格 - 返回价格列表")
    void testGetHistoryPrices_ReturnList() {
        JSONArray mockArray = new JSONArray();
        mockArray.add("[\"2024-01-01\",1000,1020,980,1005]");
        
        when(requestUtils.request3(any())).thenReturn(mockArray);
        
        List<StockPrices> prices = zxStockService.getHistoryPrices("600519");
        
        assertNotNull(prices);
        assertFalse(prices.isEmpty());
    }

    // ==================== 手续费计算测试 ====================

    @Test
    @DisplayName("手续费计算 - 高于最低值")
    void testGetPeeAmount_AboveMinimum() {
        Double fee = zxStockService.getPeeAmount(20000.0);
        
        assertEquals(10.0, fee, 0.01);
    }

    @Test
    @DisplayName("手续费计算 - 低于最低值")
    void testGetPeeAmount_BelowMinimum() {
        Double fee = zxStockService.getPeeAmount(5000.0);
        
        assertEquals(5.0, fee, 0.01);
    }

    // ==================== 参数构建测试 ====================

    @Test
    @DisplayName("参数构建 - 包含必要字段")
    void testBuildParams_ContainsRequiredFields() throws Exception {
        java.util.HashMap<String, Object> paramMap = new java.util.HashMap<>();
        paramMap.put("action", "100");
        
        java.lang.reflect.Method method = ZXStockServiceImpl.class.getDeclaredMethod("buildParams", java.util.HashMap.class);
        method.setAccessible(true);
        
        java.util.HashMap<String, Object> result = (java.util.HashMap<String, Object>) method.invoke(zxStockService, paramMap);
        
        assertNotNull(result.get("cfrom"));
        assertNotNull(result.get("tfrom"));
        assertNotNull(result.get("reqno"));
    }

    // ==================== 订单状态解析测试 ====================

    @Test
    @DisplayName("订单状态列表解析 - 正常解析")
    void testArrayToList() throws Exception {
        when(valueOperations.get("requestToken")).thenReturn("test_token");
        
        JSONArray mockArray = new JSONArray();
        mockArray.add(0, "header");
        mockArray.add(1, "600519|贵州茅台|已成|ORDER123");
        
        when(requestUtils.request2(any())).thenReturn(mockArray);
        
        java.lang.reflect.Method method = ZXStockServiceImpl.class.getDeclaredMethod("listTodayAllOrder");
        method.setAccessible(true);
        
        List<OrderStatus> statuses = (List<OrderStatus>) method.invoke(zxStockService);
        
        assertNotNull(statuses);
    }

    // ==================== IStockService接口实现验证 ====================

    @Test
    @DisplayName("IStockService接口 - 验证实现")
    void testImplementsIStockService() {
        assertTrue(zxStockService instanceof IStockService);
    }
}
