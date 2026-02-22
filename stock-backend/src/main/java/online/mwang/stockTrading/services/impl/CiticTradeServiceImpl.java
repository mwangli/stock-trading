package online.mwang.stockTrading.services.impl;

import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.entities.AccountInfo;
import online.mwang.stockTrading.entities.OrderInfo;
import online.mwang.stockTrading.entities.Position;
import online.mwang.stockTrading.entities.StockInfo;
import online.mwang.stockTrading.repositories.AccountInfoRepository;
import online.mwang.stockTrading.repositories.ModelInfoRepository;
import online.mwang.stockTrading.repositories.OrderInfoRepository;
import online.mwang.stockTrading.repositories.PositionRepository;
import online.mwang.stockTrading.repositories.StockInfoRepository;
import online.mwang.stockTrading.results.OrderResult;
import online.mwang.stockTrading.utils.OcrUtils;
import online.mwang.stockTrading.utils.RequestUtils;
import online.mwang.stockTrading.utils.SleepUtils;
import online.mwang.stockTrading.services.StockInfoService;
import online.mwang.stockTrading.services.TradeExecutionService;
import online.mwang.stockTrading.services.TradingRecordService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 中信证券交易服务实现
 * V2.0架构：基于MySQL的交易执行服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CiticTradeServiceImpl implements TradeExecutionService {

    public static final String TOKEN = "requestToken";
    public static final int LOGIN_RETRY_TIMES = 10;
    public static final int TOKEN_EXPIRE_MINUTES = 30;
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String TIME_FORMAT = "HH:mm:ss";
    
    // 交易时间段
    public static final String MORNING_SESSION_START = "09:30";
    public static final String MORNING_SESSION_END = "11:30";
    public static final String AFTERNOON_SESSION_START = "13:00";
    public static final String AFTERNOON_SESSION_END = "15:00";
    public static final String BUY_TIME_START = "14:50";
    public static final String BUY_TIME_END = "14:55";
    
    // 止损参数
    public static final BigDecimal STOP_LOSS_RATIO = new BigDecimal("0.03"); // 3%止损
    
    private final RequestUtils requestUtils;
    private final OcrUtils ocrUtils;
    private final StockInfoService stockInfoService;
    private final TradingRecordService tradingRecordService;
    private final AccountInfoRepository accountInfoRepository;
    private final PositionRepository positionRepository;
    private final OrderInfoRepository orderInfoRepository;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final StockInfoRepository stockInfoRepository;
    private final ModelInfoRepository strategyRepository;
    private final SleepUtils sleepUtils;

    @Override
    public String getToken() {
        return redisTemplate.opsForValue().get(TOKEN);
    }

    @Override
    public void setToken(String token) {
        if (token == null) return;
        redisTemplate.opsForValue().set(TOKEN, token);
    }

    @Override
    public Integer cancelAllOrder() {
        List<OrderInfo> todayOrders = getTodayOrder();
        int count = 0;
        for (OrderInfo order : todayOrders) {
            // 模拟撤单逻辑 - 实际需要调用券商API
            if ("已报".equals(order.getStatus()) || "待报".equals(order.getStatus())) {
                order.setStatus("已撤单");
                order.setUpdateTime(new Date());
                orderInfoRepository.update(order);
                count++;
            }
        }
        log.info("共取消{}条无效订单!", count);
        return count;
    }

    @Override
    public AccountInfo getAccountInfo() {
        AccountInfo account = accountInfoRepository.getLast();
        if (account == null) {
            // 初始化账户信息
            account = new AccountInfo();
            account.setTotalAsset(new BigDecimal("100000"));
            account.setAvailableCash(new BigDecimal("100000"));
            account.setMarketValue(BigDecimal.ZERO);
            account.setFrozenAmount(BigDecimal.ZERO);
            account.setDailyPnl(BigDecimal.ZERO);
            account.setMonthlyPnl(BigDecimal.ZERO);
            account.setTotalPnl(BigDecimal.ZERO);
            account.setPositionCount(0);
            account.setCreateTime(LocalDateTime.now());
            account.setUpdateTime(LocalDateTime.now());
            accountInfoRepository.save(account);
        }
        return account;
    }

    @Override
    public List<OrderInfo> getTodayOrder() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        List<OrderInfo> allOrders = orderInfoRepository.findAll();
        List<OrderInfo> todayOrders = new ArrayList<>();
        for (OrderInfo order : allOrders) {
            if (today.equals(order.getDate())) {
                todayOrders.add(order);
            }
        }
        return todayOrders;
    }

    @Override
    public List<OrderInfo> getHistoryOrder() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        List<OrderInfo> allOrders = orderInfoRepository.findAll();
        List<OrderInfo> historyOrders = new ArrayList<>();
        for (OrderInfo order : allOrders) {
            if (!today.equals(order.getDate()) && "已成交".equals(order.getStatus())) {
                historyOrders.add(order);
            }
        }
        return historyOrders;
    }

    @Override
    public Double getFeeAmount(Double amount) {
        // 手续费计算：佣金最低5元，印花税千分之一（卖出）
        if (amount == null || amount <= 0) {
            return 0.0;
        }
        return Math.max(5.0, amount * 0.0003);
    }

    @Override
    public JSONObject submitOrder(String type, String code, Double price, Double number) {
        // 模拟提交订单 - 实际需要调用券商API
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("orderId", UUID.randomUUID().toString().substring(0, 8));
        result.put("message", "订单已提交");
        return result;
    }

    @Override
    public boolean waitOrderSuccess(String answerNo) {
        // 模拟等待订单成功 - 实际需要轮询券商API
        return true;
    }

    @Override
    public Double getCurrentPrice(String code) {
        // 从Redis或数据库获取实时价格
        String priceKey = "stock:price:" + code;
        String price = redisTemplate.opsForValue().get(priceKey);
        if (price != null) {
            return Double.parseDouble(price);
        }
        // 如果没有缓存价格，返回模拟价格
        return 10.0;
    }

    @Override
    public OrderResult executeBuy(String stockCode, int quantity) {
        log.info("执行买入: stockCode={}, quantity={}", stockCode, quantity);
        
        // 1. 检查买入时间
        if (!isWithinBuyTime()) {
            return OrderResult.fail("不在买入时间段(14:50-14:55)内");
        }
        
        // 2. 获取股票信息
        StockInfo stockInfo = getStockInfo(stockCode);
        if (stockInfo == null) {
            return OrderResult.fail("股票信息不存在: " + stockCode);
        }
        
        // 3. 获取当前价格
        Double currentPrice = getCurrentPrice(stockCode);
        if (currentPrice == null || currentPrice <= 0) {
            return OrderResult.fail("无法获取股票价格: " + stockCode);
        }
        
        // 4. 计算买入金额
        double totalAmount = currentPrice * quantity;
        double fee = getFeeAmount(totalAmount);
        
        // 5. 检查账户资金
        AccountInfo account = getAccountInfo();
        BigDecimal availableCash = account.getAvailableCash();
        BigDecimal required = new BigDecimal(totalAmount).add(new BigDecimal(fee));
        
        if (availableCash.compareTo(required) < 0) {
            return OrderResult.fail("资金不足，可用: " + availableCash + ", 需要: " + required);
        }
        
        // 6. 提交订单
        JSONObject orderResult = submitOrder("买入", stockCode, currentPrice, (double) quantity);
        if (!(Boolean) orderResult.getOrDefault("success", false)) {
            return OrderResult.fail("订单提交失败: " + orderResult.getString("message"));
        }
        
        String orderId = orderResult.getString("orderId");
        
        // 7. 创建订单记录
        createOrderRecord(stockCode, stockInfo.getName(), "买入", quantity, currentPrice, orderId);
        
        // 8. 更新持仓
        updatePositionAfterBuy(stockCode, stockInfo.getName(), quantity, currentPrice);
        
        // 9. 更新账户资金
        updateAccountAfterBuy(required);
        
        log.info("买入成功: stockCode={}, quantity={}, price={}, orderId={}", stockCode, quantity, currentPrice, orderId);
        return OrderResult.success(orderId, stockCode, "BUY", quantity, currentPrice, fee);
    }

    @Override
    public OrderResult executeSell(String stockCode, int quantity) {
        log.info("执行卖出: stockCode={}, quantity={}", stockCode, quantity);
        
        // 1. 获取持仓
        Position position = positionRepository.findByStockCode(stockCode);
        if (position == null || position.getQuantity() == null || position.getQuantity() <= 0) {
            return OrderResult.fail("无持仓或持仓不足: " + stockCode);
        }
        
        // 2. 检查T+1限制
        if (!canSell(position)) {
            return OrderResult.fail("T+1限制，该持仓当日买入，次交易日才能卖出");
        }
        
        // 3. 获取股票信息
        StockInfo stockInfo = getStockInfo(stockCode);
        
        // 4. 获取当前价格
        Double currentPrice = getCurrentPrice(stockCode);
        if (currentPrice == null || currentPrice <= 0) {
            return OrderResult.fail("无法获取股票价格: " + stockCode);
        }
        
        // 5. 计算卖出金额
        double totalAmount = currentPrice * quantity;
        double fee = getFeeAmount(totalAmount);
        double stampDuty = 0.001; // 印花税仅卖出收取
        double stampTax = totalAmount * stampDuty;
        double netProfit = totalAmount - fee - stampTax;
        
        // 6. 提交订单
        JSONObject orderResult = submitOrder("卖出", stockCode, currentPrice, (double) quantity);
        if (!(Boolean) orderResult.getOrDefault("success", false)) {
            return OrderResult.fail("订单提交失败: " + orderResult.getString("message"));
        }
        
        String orderId = orderResult.getString("orderId");
        
        // 7. 创建订单记录
        String stockName = stockInfo != null ? stockInfo.getName() : stockCode;
        createOrderRecord(stockCode, stockName, "卖出", quantity, currentPrice, orderId);
        
        // 8. 更新持仓
        updatePositionAfterSell(position, quantity, currentPrice);
        
        // 9. 更新账户资金
        updateAccountAfterSell(new BigDecimal(netProfit));
        
        log.info("卖出成功: stockCode={}, quantity={}, price={}, orderId={}, 净收益={}", 
                stockCode, quantity, currentPrice, orderId, netProfit);
        return OrderResult.success(orderId, stockCode, "SELL", quantity, currentPrice, fee + stampTax);
    }

    @Override
    public List<Position> getHoldingPositions() {
        return positionRepository.findHoldingPositions();
    }

    @Override
    public void retrySellIfNeeded() {
        log.info("开始检查止损...");
        List<Position> positions = getHoldingPositions();
        
        for (Position position : positions) {
            try {
                // 检查是否可以卖出（T+1）
                if (!canSell(position)) {
                    continue;
                }
                
                // 获取当前价格
                Double currentPrice = getCurrentPrice(position.getStockCode());
                if (currentPrice == null) {
                    continue;
                }
                
                // 计算亏损比例
                BigDecimal avgCost = position.getAvgCost();
                if (avgCost == null || avgCost.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                
                BigDecimal currentPriceBd = new BigDecimal(currentPrice);
                BigDecimal lossRatio = avgCost.subtract(currentPriceBd).divide(avgCost, 4, BigDecimal.ROUND_HALF_UP);
                
                // 检查是否触发止损
                if (lossRatio.compareTo(STOP_LOSS_RATIO) >= 0) {
                    log.warn("触发止损: stockCode={}, 亏损比例={}, 成本价={}, 当前价={}", 
                            position.getStockCode(), lossRatio, avgCost, currentPrice);
                    
                    // 执行止损卖出
                    int sellQuantity = position.getQuantity();
                    OrderResult result = executeSell(position.getStockCode(), sellQuantity);
                    
                    if (result.isSuccess()) {
                        log.info("止损卖出成功: stockCode={}, quantity={}", position.getStockCode(), sellQuantity);
                    } else {
                        log.error("止损卖出失败: stockCode={}, error={}", position.getStockCode(), result.getErrorMessage());
                    }
                }
            } catch (Exception e) {
                log.error("止损检查异常: stockCode={}, error={}", position.getStockCode(), e.getMessage());
            }
        }
    }

    // ==================== 辅助方法 ====================
    
    /**
     * 检查是否在买入时间段
     */
    private boolean isWithinBuyTime() {
        LocalDateTime now = LocalDateTime.now();
        String timeStr = now.format(DateTimeFormatter.ofPattern(TIME_FORMAT));
        return timeStr.compareTo(BUY_TIME_START) >= 0 && timeStr.compareTo(BUY_TIME_END) <= 0;
    }
    
    /**
     * 检查持仓是否满足T+1卖出条件
     */
    private boolean canSell(Position position) {
        if (position.getOpenDate() == null) {
            return true; // 没有开仓日期，可以卖出
        }
        LocalDate openDate = position.getOpenDate();
        LocalDate today = LocalDate.now();
        // T+1: 今日与开仓日不是同一天即可卖出
        return !openDate.equals(today);
    }
    
    /**
     * 获取股票信息
     */
    private StockInfo getStockInfo(String stockCode) {
        return stockInfoRepository.findByCode(stockCode);
    }
    
    /**
     * 创建订单记录
     */
    private void createOrderRecord(String code, String name, String type, int quantity, 
                                   Double price, String orderId) {
        OrderInfo order = new OrderInfo();
        order.setCode(code);
        order.setName(name);
        order.setType(type);
        order.setNumber((double) quantity);
        order.setPrice(price);
        order.setAmount(price * quantity);
        order.setPeer(0.0);
        order.setStatus("已报");
        order.setDate(LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
        order.setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT)));
        order.setAnswerNo(orderId);
        order.setCreateTime(new Date());
        order.setUpdateTime(new Date());
        orderInfoRepository.save(order);
    }
    
    /**
     * 买入后更新持仓
     */
    private void updatePositionAfterBuy(String stockCode, String stockName, int quantity, Double price) {
        Position position = positionRepository.findByStockCode(stockCode);
        BigDecimal priceBd = new BigDecimal(price);
        
        if (position == null) {
            // 新建持仓
            position = new Position();
            position.setStockCode(stockCode);
            position.setStockName(stockName);
            position.setQuantity(quantity);
            position.setAvailableQuantity(quantity);
            position.setFrozenQuantity(0);
            position.setAvgCost(priceBd);
            position.setCurrentPrice(priceBd);
            position.setMarketValue(priceBd.multiply(new BigDecimal(quantity)));
            position.setUnrealizedPnl(BigDecimal.ZERO);
            position.setUnrealizedPnlRatio(BigDecimal.ZERO);
            position.setOpenDate(LocalDate.now());
            position.setPositionSide("LONG");
            position.setStatus("HOLDING");
            position.setCreateTime(LocalDateTime.now());
            position.setUpdateTime(LocalDateTime.now());
        } else {
            // 更新持仓（计算新的平均成本）
            int oldQty = position.getQuantity() != null ? position.getQuantity() : 0;
            BigDecimal oldCost = position.getAvgCost() != null ? position.getAvgCost() : BigDecimal.ZERO;
            
            int newQty = oldQty + quantity;
            BigDecimal newCost = oldCost.multiply(new BigDecimal(oldQty))
                    .add(priceBd.multiply(new BigDecimal(quantity))).divide(new BigDecimal(newQty), 4, BigDecimal.ROUND_HALF_UP);
            
            position.setQuantity(newQty);
            position.setAvailableQuantity(newQty);
            position.setAvgCost(newCost);
            position.setCurrentPrice(priceBd);
            position.setMarketValue(priceBd.multiply(new BigDecimal(newQty)));
            position.setUpdateTime(LocalDateTime.now());
            
            // 更新未实现盈亏
            position.calculateUnrealizedPnl();
        }
        
        positionRepository.save(position);
    }
    
    /**
     * 卖出后更新持仓
     */
    private void updatePositionAfterSell(Position position, int quantity, Double currentPrice) {
        int remainingQty = position.getQuantity() - quantity;
        BigDecimal priceBd = new BigDecimal(currentPrice);
        
        if (remainingQty <= 0) {
            // 全部卖出，平仓
            position.setQuantity(0);
            position.setAvailableQuantity(0);
            position.setStatus("SOLD");
            position.setUpdateTime(LocalDateTime.now());
        } else {
            // 部分卖出
            position.setQuantity(remainingQty);
            position.setAvailableQuantity(remainingQty);
            position.setCurrentPrice(priceBd);
            position.setMarketValue(priceBd.multiply(new BigDecimal(remainingQty)));
            position.setUpdateTime(LocalDateTime.now());
            
            // 更新未实现盈亏
            position.calculateUnrealizedPnl();
        }
        
        positionRepository.update(position);
    }
    
    /**
     * 买入后更新账户资金
     */
    private void updateAccountAfterBuy(BigDecimal amount) {
        AccountInfo account = getAccountInfo();
        account.setAvailableCash(account.getAvailableCash().subtract(amount));
        account.setMarketValue(account.getMarketValue().add(amount));
        account.setUpdateTime(LocalDateTime.now());
        accountInfoRepository.update(account);
    }
    
    /**
     * 卖出后更新账户资金
     */
    private void updateAccountAfterSell(BigDecimal profit) {
        AccountInfo account = getAccountInfo();
        account.setAvailableCash(account.getAvailableCash().add(profit));
        
        // 更新盈亏
        BigDecimal dailyPnl = account.getDailyPnl() != null ? account.getDailyPnl() : BigDecimal.ZERO;
        account.setDailyPnl(dailyPnl.add(profit));
        
        BigDecimal totalPnl = account.getTotalPnl() != null ? account.getTotalPnl() : BigDecimal.ZERO;
        account.setTotalPnl(totalPnl.add(profit));
        
        account.setUpdateTime(LocalDateTime.now());
        accountInfoRepository.update(account);
    }
}
