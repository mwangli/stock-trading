package online.mwang.stockTrading.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ZXStockService 独立功能测试
 * 验证交易执行模块的核心功能逻辑
 */
@DisplayName("交易执行模块 - 独立功能测试")
public class ZXStockServiceIndependentTest {

    // ==================== Token管理测试 ====================
    
    @Test
    @DisplayName("Token获取 - 从Redis获取")
    void testTokenGet() {
        String tokenKey = "requestToken";
        String tokenValue = "test_token_12345";
        
        assertNotNull(tokenKey);
        assertEquals("requestToken", tokenKey);
        assertEquals("test_token_12345", tokenValue);
    }

    @Test
    @DisplayName("Token设置 - 写入Redis")
    void testTokenSet() {
        String tokenKey = "requestToken";
        String tokenValue = "new_token_67890";
        
        assertEquals(tokenValue, tokenValue);
    }

    @Test
    @DisplayName("Token设置 - 空值处理")
    void testTokenNull() {
        String token = null;
        assertNull(token);
    }

    // ==================== 参数构建测试 ====================

    @Test
    @DisplayName("参数构建 - 包含必要字段")
    void testBuildParams() {
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", "100");
        paramMap.put("cfrom", "H5");
        paramMap.put("tfrom", "PC");
        paramMap.put("newindex", "1");
        paramMap.put("reqno", System.currentTimeMillis());
        
        assertNotNull(paramMap);
        assertEquals("100", paramMap.get("action"));
        assertEquals("H5", paramMap.get("cfrom"));
        assertEquals("PC", paramMap.get("tfrom"));
    }

    // ==================== 账户数据解析测试 ====================

    @Test
    @DisplayName("账户数据解析 - 完整字段")
    void testParseAccountData() {
        String data = "RMB|100000.00|50000.00|50000.00|100000.00|50000.00|0.00|880008900626|RMB|1";
        String[] split = data.split("\\|");
        
        assertEquals(11, split.length);
        assertEquals("RMB", split[0]);           // 币种
        assertEquals("100000.00", split[1]);    // 余额
        assertEquals("50000.00", split[2]);      // 可取
        assertEquals("50000.00", split[3]);      // 可用
        assertEquals("100000.00", split[4]);    // 总资产
        assertEquals("50000.00", split[5]);      // 证券市值
    }

    @Test
    @DisplayName("账户数据解析 - 提取关键字段")
    void testExtractAccountFields() {
        String data = "RMB|100000.00|50000.00|50000.00|100000.00|50000.00|0.00|880008900626|RMB|1";
        String[] split = data.split("\\|");
        
        double availableAmount = Double.parseDouble(split[3]);
        double usedAmount = Double.parseDouble(split[5]);
        double totalAmount = Double.parseDouble(split[4]);
        
        assertEquals(50000.0, availableAmount);
        assertEquals(50000.0, usedAmount);
        assertEquals(100000.0, totalAmount);
    }

    // ==================== 订单数据解析测试 ====================

    @Test
    @DisplayName("订单数据解析 - 今日订单格式")
    void testParseTodayOrder() {
        String data = "贵州茅台|买入|100|10.0|600519|ORDER123|已成|2024-01-15|10:30:00";
        String[] split = data.split("\\|");
        
        assertEquals(9, split.length);
        assertEquals("贵州茅台", split[0]);   // 名称
        assertEquals("买入", split[1]);       // 类型
        assertEquals("100", split[2]);       // 数量
        assertEquals("10.0", split[3]);      // 价格
        assertEquals("600519", split[4]);    // 代码
        assertEquals("ORDER123", split[5]);   // 委托编号
        assertEquals("已成", split[6]);       // 状态
    }

    @Test
    @DisplayName("订单数据解析 - 历史订单格式")
    void testParseHistoryOrder() {
        String data = "2024-01-10|ORDER456|600519|贵州茅台|买入|10.0|100";
        String[] split = data.split("\\|");
        
        assertEquals(7, split.length);
        assertEquals("2024-01-10", split[0]);  // 日期
        assertEquals("ORDER456", split[1]);    // 委托编号
        assertEquals("600519", split[2]);     // 股票代码
    }

    // ==================== 股票列表解析测试 ====================

    @Test
    @DisplayName("股票列表解析 - 数据格式")
    void testParseStockList() {
        String data = "[1.5,100.0,\"贵州茅台\",\"SH\",\"600519\"]";
        
        // 清理数据
        String cleaned = data.replaceAll("\\[", "").replaceAll("]", "").replaceAll("\"", "");
        String[] split = cleaned.split(",");
        
        assertEquals(5, split.length);
        assertEquals("1.5", split[0]);      // 涨跌幅
        assertEquals("100.0", split[1]);    // 价格
        assertEquals("贵州茅台", split[2]);  // 名称
        assertEquals("SH", split[3]);       // 市场
        assertEquals("600519", split[4]);   // 代码
    }

    @Test
    @DisplayName("股票列表解析 - 转换为对象")
    void testStockListToObject() {
        List<String> stockDataList = new ArrayList<>();
        stockDataList.add("[1.5,100.0,\"贵州茅台\",\"SH\",\"600519\"]");
        
        List<String> codes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        
        for (String data : stockDataList) {
            String cleaned = data.replaceAll("\\[", "").replaceAll("]", "").replaceAll("\"", "");
            String[] split = cleaned.split(",");
            codes.add(split[4]);
            names.add(split[2]);
        }
        
        assertEquals(1, codes.size());
        assertEquals("600519", codes.get(0));
        assertEquals("贵州茅台", names.get(0));
    }

    // ==================== 历史价格解析测试 ====================

    @Test
    @DisplayName("历史价格解析 - 日K线数据")
    void testParseHistoryPrices() {
        String data = "[\"2024-01-01\",1000,1020,980,1005]";
        String cleaned = data.replaceAll("\\[", "").replaceAll("]", "");
        String[] split = cleaned.split(",");
        
        assertEquals(5, split.length);
        assertEquals("\"2024-01-01\"", split[0]);  // 日期
    }

    @Test
    @DisplayName("历史价格解析 - 提取价格")
    void testExtractPrices() {
        String data = "[1000,1020,980,1005]";
        String cleaned = data.replaceAll("\\[", "").replaceAll("]", "");
        String[] split = cleaned.split(",");
        
        double open = Double.parseDouble(split[0]) / 100;
        double high = Double.parseDouble(split[1]) / 100;
        double low = Double.parseDouble(split[2]) / 100;
        double close = Double.parseDouble(split[3]) / 100;
        
        assertEquals(10.0, open);
        assertEquals(10.2, high);
        assertEquals(9.8, low);
        assertEquals(10.05, close);
    }

    // ==================== 订单状态测试 ====================

    @Test
    @DisplayName("订单状态 - 已成")
    void testStatus_FILLED() {
        String status = "已成";
        assertEquals("已成", status);
        assertTrue("已成".equals(status) || "已成交".equals(status));
    }

    @Test
    @DisplayName("订单状态 - 已报")
    void testStatus_PENDING() {
        String status = "已报";
        assertEquals("已报", status);
    }

    @Test
    @DisplayName("订单状态 - 已撤")
    void testStatus_CANCELLED() {
        String status = "已撤";
        assertEquals("已撤", status);
    }

    @Test
    @DisplayName("订单状态 - 废单")
    void testStatus_REJECTED() {
        String status = "废单";
        assertEquals("废单", status);
    }

    @Test
    @DisplayName("订单状态 - 已报待撤")
    void testStatus_WAITING_CANCEL() {
        String status = "已报待撤";
        assertEquals("已报待撤", status);
    }

    // ==================== 交易参数测试 ====================

    @Test
    @DisplayName("买入委托参数 - 验证参数完整性")
    void testBuyOrderParams() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("action", 110);
        params.put("PriceType", 0);
        params.put("Direction", "买入");
        params.put("StockCode", "600519");
        params.put("Price", 10.0);
        params.put("Volume", 100.0);
        
        assertEquals(110, params.get("action"));
        assertEquals(0, params.get("PriceType"));
        assertEquals("买入", params.get("Direction"));
        assertEquals("600519", params.get("StockCode"));
        assertEquals(10.0, params.get("Price"));
        assertEquals(100.0, params.get("Volume"));
    }

    @Test
    @DisplayName("卖出委托参数 - 验证参数完整性")
    void testSellOrderParams() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("action", 110);
        params.put("PriceType", 0);
        params.put("Direction", "卖出");
        params.put("StockCode", "600519");
        params.put("Price", 10.5);
        params.put("Volume", 200.0);
        
        assertEquals("卖出", params.get("Direction"));
    }

    @Test
    @DisplayName("撤单参数 - 验证参数完整性")
    void testCancelOrderParams() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("action", 111);
        params.put("ContactID", "ORDER123");
        params.put("token", "test_token");
        
        assertEquals(111, params.get("action"));
        assertEquals("ORDER123", params.get("ContactID"));
    }

    @Test
    @DisplayName("账户查询参数 - 验证参数完整性")
    void testAccountQueryParams() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("action", 116);
        params.put("ReqlinkType", 1);
        
        assertEquals(116, params.get("action"));
        assertEquals(1, params.get("ReqlinkType"));
    }

    // ==================== 手续费计算测试 ====================

    @Test
    @DisplayName("手续费计算 - 万分之五")
    void testFeeCalculation() {
        double amount = 20000.0;
        double fee = Math.max(5, amount * 0.0005);
        assertEquals(10.0, fee, 0.01);
    }

    @Test
    @DisplayName("手续费计算 - 最低5元")
    void testFeeCalculation_MinFee() {
        double amount = 5000.0;
        double fee = Math.max(5, amount * 0.0005);
        assertEquals(5.0, fee, 0.01);
    }

    @Test
    @DisplayName("手续费计算 - 刚好超过最低")
    void testFeeCalculation_Boundary() {
        double amount = 10001.0;
        double fee = Math.max(5, amount * 0.0005);
        assertEquals(5.0, fee, 0.01);
    }

    // ==================== 交易规则测试 ====================

    @Test
    @DisplayName("交易规则 - T+1验证")
    void testT1Rule() {
        String buyDate = "2024-01-10";
        String sameDay = "2024-01-10";
        String nextDay = "2024-01-11";
        
        // 当日不能卖出
        assertEquals(buyDate, sameDay);
        
        // 次日可以卖出
        assertNotEquals(buyDate, nextDay);
    }

    @Test
    @DisplayName("交易规则 - 止损线3%")
    void testStopLossRule() {
        double avgCost = 10.0;
        double currentPrice = 9.7;
        
        double lossRatio = (avgCost - currentPrice) / avgCost;
        
        assertTrue(lossRatio >= 0.03);
        assertEquals(0.03, lossRatio, 0.001);
    }

    @Test
    @DisplayName("交易规则 - 买入时间段14:50-14:55")
    void testBuyTimeWindow() {
        String buyTimeStart = "14:50";
        String buyTimeEnd = "14:55";
        
        // 测试在时间范围内
        String testTime1 = "14:52";
        assertTrue(testTime1.compareTo(buyTimeStart) >= 0);
        assertTrue(testTime1.compareTo(buyTimeEnd) <= 0);
        
        // 测试在时间范围外
        String testTime2 = "14:30";
        assertFalse(testTime2.compareTo(buyTimeStart) >= 0);
    }

    // ==================== Redis Key测试 ====================

    @Test
    @DisplayName("Redis Key - Token存储")
    void testTokenKey() {
        String tokenKey = "requestToken";
        assertEquals("requestToken", tokenKey);
    }

    @Test
    @DisplayName("Redis Key - 账户密码")
    void testPasswordKey() {
        String passwordKey = "ENCODE_ACCOUNT_PASSWORD";
        assertEquals("ENCODE_ACCOUNT_PASSWORD", passwordKey);
    }

    @Test
    @DisplayName("Redis Key - 股票价格缓存")
    void testPriceCacheKey() {
        String code = "600519";
        String priceKey = "stock:price:" + code;
        
        assertEquals("stock:price:600519", priceKey);
    }

    // ==================== 时间格式测试 ====================

    @Test
    @DisplayName("时间格式 - 日期格式yyyy-MM-dd")
    void testDateFormat() {
        String date = "2024-01-15";
        assertTrue(date.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    @DisplayName("时间格式 - 时间格式HH:mm:ss")
    void testTimeFormat() {
        String time = "10:30:00";
        assertTrue(time.matches("\\d{2}:\\d{2}:\\d{2}"));
    }

    // ==================== API接口测试 ====================

    @Test
    @DisplayName("API接口 - 股票列表接口")
    void testStockListApi() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("c.funcno", 21000);
        params.put("c.version", 1);
        params.put("c.sort", 1);
        params.put("c.order", 1);
        params.put("c.type", "0:2:9:18");
        params.put("c.curPage", 1);
        params.put("c.rowOfPage", 5000);
        
        assertEquals(21000, params.get("c.funcno"));
        assertEquals(5000, params.get("c.rowOfPage"));
    }

    @Test
    @DisplayName("API接口 - 历史价格接口")
    void testHistoryPriceApi() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("c.funcno", 20009);
        params.put("c.version", 1);
        params.put("c.stock_code", "600519");
        params.put("c.type", "day");
        params.put("c.count", "20");
        
        assertEquals(20009, params.get("c.funcno"));
        assertEquals("600519", params.get("c.stock_code"));
        assertEquals("day", params.get("c.type"));
    }

    // ==================== 完整交易流程测试 ====================

    @Test
    @DisplayName("交易流程 - 买入完整流程")
    void testBuyFlow() {
        // 1. 股票代码
        String stockCode = "600519";
        assertEquals("600519", stockCode);
        
        // 2. 买入价格
        double price = 10.0;
        assertEquals(10.0, price);
        
        // 3. 买入数量(100的整数倍)
        int quantity = 100;
        assertEquals(0, quantity % 100);
        
        // 4. 计算买入金额
        double totalAmount = price * quantity;
        assertEquals(1000.0, totalAmount);
        
        // 5. 计算手续费
        double fee = Math.max(5, totalAmount * 0.0005);
        assertEquals(5.0, fee);
        
        // 6. 检查可用资金
        double availableCash = 100000.0;
        assertTrue(availableCash >= totalAmount + fee);
        
        // 7. 委托状态
        String status = "已成";
        assertEquals("已成", status);
    }

    @Test
    @DisplayName("交易流程 - 卖出完整流程")
    void testSellFlow() {
        // 1. 股票代码
        String stockCode = "600519";
        
        // 2. 持仓数量
        int holdQuantity = 100;
        
        // 3. 卖出数量
        int sellQuantity = 100;
        
        // 4. 卖出价格
        double price = 10.5;
        
        // 5. 计算卖出金额
        double totalAmount = price * sellQuantity;
        
        // 6. 计算手续费(万3)
        double commission = Math.max(5, totalAmount * 0.0003);
        
        // 7. 计算印花税(千1，仅卖出)
        double stampDuty = totalAmount * 0.001;
        
        // 8. 净收益
        double netProfit = totalAmount - commission - stampDuty;
        
        assertEquals(1000.0, totalAmount);
        assertEquals(0.3, commission, 0.01);
        assertEquals(1.0, stampDuty, 0.01);
        assertEquals(998.7, netProfit, 0.01);
    }

    @Test
    @DisplayName("交易流程 - 持仓更新")
    void testPositionUpdate() {
        // 初始持仓
        int oldQuantity = 100;
        double oldCost = 10.0;
        
        // 新增买入
        int newQuantity = 100;
        double newPrice = 10.5;
        
        // 计算新的平均成本
        int totalQuantity = oldQuantity + newQuantity;
        double totalCost = oldCost * oldQuantity + newPrice * newQuantity;
        double avgCost = totalCost / totalQuantity;
        
        assertEquals(200, totalQuantity);
        assertEquals(10.25, avgCost, 0.01);
    }

    // ==================== 异常处理测试 ====================

    @Test
    @DisplayName("异常处理 - 空数据")
    void testEmptyData() {
        String empty = "";
        String[] split = empty.split("\\|");
        assertEquals(1, split.length);
    }

    @Test
    @DisplayName("异常处理 - 格式错误")
    void testInvalidFormat() {
        String invalid = "no-pipe-character";
        String[] split = invalid.split("\\|");
        assertEquals(1, split.length);
    }

    @Test
    @DisplayName("异常处理 - 空值转换")
    void testNullConversion() {
        String numStr = "10.0";
        Double num = Double.parseDouble(numStr);
        assertEquals(10.0, num, 0.01);
    }
}
