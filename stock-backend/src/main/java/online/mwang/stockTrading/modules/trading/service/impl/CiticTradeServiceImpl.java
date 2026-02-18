package online.mwang.stockTrading.modules.trading.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.modules.trading.service.TradeExecutionService;
import online.mwang.stockTrading.modules.trading.entity.AccountInfo;
import online.mwang.stockTrading.modules.trading.entity.OrderInfo;
import online.mwang.stockTrading.modules.trading.entity.Position;
import online.mwang.stockTrading.modules.datacollection.entity.StockInfo;
import online.mwang.stockTrading.modules.datacollection.entity.StockPrices;
import online.mwang.stockTrading.modules.trading.mapper.AccountInfoMapper;
import online.mwang.stockTrading.modules.prediction.mapper.ModelInfoMapper;
import online.mwang.stockTrading.modules.datacollection.mapper.StockInfoMapper;
import online.mwang.stockTrading.modules.datacollection.service.StockInfoService;
import online.mwang.stockTrading.modules.trading.service.TradingRecordService;
import online.mwang.stockTrading.modules.trading.model.OrderResult;
import online.mwang.stockTrading.core.utils.DateUtils;
import online.mwang.stockTrading.core.utils.OcrUtils;
import online.mwang.stockTrading.core.utils.RequestUtils;
import online.mwang.stockTrading.core.utils.SleepUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 中信证券交易服务实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CiticTradeServiceImpl implements TradeExecutionService {

    public static final String TOKEN = "requestToken";
    public static final int LOGIN_RETRY_TIMES = 10;
    public static final int TOKEN_EXPIRE_MINUTES = 30;
    public final RequestUtils requestUtils;
    public final OcrUtils ocrUtils;
    public final StockInfoService stockInfoService;
    public final TradingRecordService tradingRecordService;
    public final AccountInfoMapper accountInfoMapper;
    public final StringRedisTemplate redisTemplate;
    public final MongoTemplate mongoTemplate;
    public final StockInfoMapper stockInfoMapper;
    public final ModelInfoMapper strategyMapper;
    public final SleepUtils sleepUtils;

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
        List<OrderInfo> orderInfos = getTodayOrder();
        log.info("共取消{}条无效订单!", orderInfos.size());
        return orderInfos.size();
    }

    @Override
    public AccountInfo getAccountInfo() {
        return null;
    }

    @Override
    public List<OrderInfo> getTodayOrder() {
        return new ArrayList<>();
    }

    @Override
    public List<OrderInfo> getHistoryOrder() {
        return new ArrayList<>();
    }

    @Override
    public Double getFeeAmount(Double amount) {
        return Math.max(5, amount * 0.0005);
    }

    @Override
    public JSONObject submitOrder(String type, String code, Double price, Double number) {
        return null;
    }

    @Override
    public boolean waitOrderSuccess(String answerNo) {
        return false;
    }

    @Override
    public Double getCurrentPrice(String code) {
        return null;
    }

    @Override
    public OrderResult executeBuy(String stockCode, int quantity) {
        return null;
    }

    @Override
    public OrderResult executeSell(String stockCode, int quantity) {
        return null;
    }

    @Override
    public List<Position> getHoldingPositions() {
        return new ArrayList<>();
    }

    @Override
    public void retrySellIfNeeded() {
    }
}
