// AI_GENERATE_START-----------
package com.stock.trading.service;

import com.stock.trading.dto.BrokerOrderDto;
import com.stock.trading.dto.BrokerOrderPageResponseDto;
import com.stock.trading.dto.BrokerTransactionQueryRequest;
import com.stock.trading.dto.TradeRecordDto;
import com.stock.trading.dto.TradeRecordPageResponseDto;
import com.stock.trading.entity.BrokerOrderEntity;
import com.stock.trading.entity.TradeRecordEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 已入库历史订单和 FIFO 汇总交易记录查询服务。
 * 所有查询均限制在当前配置账户的哈希标识内。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Service
@RequiredArgsConstructor
public class BrokerTransactionQueryService {

    private static final int RATE_SCALE = 8;

    private final BrokerIdentityService brokerIdentityService;
    private final MongoTemplate mongoTemplate;

    /**
     * 分页查询当前账户已入库的已成交历史订单。
     *
     * @param request 查询条件
     * @return 历史订单分页结果
     */
    public BrokerOrderPageResponseDto queryOrders(BrokerTransactionQueryRequest request) {
        QueryContext context = validate(request);
        Criteria criteria = buildOrderCriteria(request);
        long total = mongoTemplate.count(Query.query(criteria), BrokerOrderEntity.class);
        Query query = Query.query(criteria).with(PageRequest.of(context.current() - 1, context.pageSize(),
                Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.desc("id"))));
        List<BrokerOrderEntity> items = mongoTemplate.find(query, BrokerOrderEntity.class);
        return BrokerOrderPageResponseDto.builder()
                .items(items.stream().map(this::toOrderDto).toList())
                .total(total)
                .current(context.current())
                .pageSize(context.pageSize())
                .build();
    }

    /**
     * 分页查询当前账户由历史订单汇总得到的交易记录，仅支持按股票代码或名称筛选。
     *
     * @param request 查询条件
     * @return 交易记录分页结果
     */
    public TradeRecordPageResponseDto queryTrades(BrokerTransactionQueryRequest request) {
        QueryContext context = validate(request);
        Criteria criteria = buildTradeCriteria(request);
        long total = mongoTemplate.count(Query.query(criteria), TradeRecordEntity.class);
        Query query = Query.query(criteria).with(PageRequest.of(context.current() - 1, context.pageSize(),
                Sort.by(Sort.Direction.DESC, "buyTime")));
        List<TradeRecordEntity> items = mongoTemplate.find(query, TradeRecordEntity.class);
        return TradeRecordPageResponseDto.builder()
                .items(items.stream().map(this::toTradeRecordDto).toList())
                .total(total)
                .current(context.current())
                .pageSize(context.pageSize())
                .build();
    }

    private Criteria buildOrderCriteria(BrokerTransactionQueryRequest request) {
        Criteria criteria = Criteria.where("brokerCode").is(brokerIdentityService.getBrokerCode())
                .and("accountId").is(brokerIdentityService.getAccountId())
                .and("status").is("FILLED")
                .and("filledQuantity").gt(0)
                .and("averageFillPrice").gt(BigDecimal.ZERO);
        if (hasText(request.getDirection())) {
            criteria.and("side").is(request.getDirection().trim().toUpperCase(Locale.ROOT));
        }
        if (hasText(request.getKeyword())) {
            Pattern pattern = containsPattern(request.getKeyword());
            criteria.andOperator(new Criteria().orOperator(
                    Criteria.where("stockCode").regex(pattern),
                    Criteria.where("stockName").regex(pattern),
                    Criteria.where("brokerOrderNo").regex(pattern)));
        }
        if (request.getStartDate() != null) {
            criteria.and("tradeDate").gte(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            criteria.and("tradeDate").lte(request.getEndDate());
        }
        return criteria;
    }

    private Criteria buildTradeCriteria(BrokerTransactionQueryRequest request) {
        Criteria criteria = Criteria.where("brokerCode").is(brokerIdentityService.getBrokerCode())
                .and("accountId").is(brokerIdentityService.getAccountId());
        if (hasText(request.getKeyword())) {
            Pattern pattern = containsPattern(request.getKeyword());
            criteria.andOperator(new Criteria().orOperator(
                    Criteria.where("stockCode").regex(pattern),
                    Criteria.where("stockName").regex(pattern)));
        }
        if (request.getStartDate() != null) {
            criteria.and("buyTime").gte(request.getStartDate().atStartOfDay());
        }
        if (request.getEndDate() != null) {
            criteria.and("buyTime").lt(request.getEndDate().plusDays(1).atStartOfDay());
        }
        return criteria;
    }

    private QueryContext validate(BrokerTransactionQueryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("查询条件不能为空");
        }
        if (request.getStartDate() != null && request.getEndDate() != null
                && request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        if (hasText(request.getDirection())
                && !"BUY".equalsIgnoreCase(request.getDirection())
                && !"SELL".equalsIgnoreCase(request.getDirection())) {
            throw new IllegalArgumentException("买卖方向仅支持 BUY 或 SELL");
        }
        int current = Math.max(1, request.getCurrent() != null ? request.getCurrent() : 1);
        int pageSize = Math.min(100, Math.max(1, request.getPageSize() != null ? request.getPageSize() : 20));
        return new QueryContext(current, pageSize);
    }

    private BrokerOrderDto toOrderDto(BrokerOrderEntity entity) {
        return BrokerOrderDto.builder()
                .orderId(entity.getBrokerOrderNo())
                .stockCode(entity.getStockCode())
                .stockName(entity.getStockName())
                .direction(entity.getSide())
                .orderPrice(entity.getOrderPrice())
                .orderQuantity(entity.getOrderQuantity())
                .filledQuantity(entity.getFilledQuantity())
                .averageFillPrice(entity.getAverageFillPrice())
                .status(entity.getStatus())
                .rawStatus(entity.getRawStatus())
                .market(entity.getMarket())
                .orderTime(entity.getSubmittedAt())
                .build();
    }

    private TradeRecordDto toTradeRecordDto(TradeRecordEntity entity) {
        return TradeRecordDto.builder()
                .stockCode(entity.getStockCode())
                .stockName(entity.getStockName())
                .status(entity.getStatus())
                .buyOrderNo(entity.getBuyOrderNo())
                .sellOrderNos(entity.getSellOrderNos())
                .buyTime(entity.getBuyTime())
                .firstSellTime(entity.getFirstSellTime())
                .lastSellTime(entity.getLastSellTime())
                .buyQuantity(entity.getBuyQuantity())
                .soldQuantity(entity.getSoldQuantity())
                .remainingQuantity(entity.getRemainingQuantity())
                .buyPrice(entity.getBuyPrice())
                .averageSellPrice(entity.getAverageSellPrice())
                .buyAmount(entity.getBuyAmount())
                .sellAmount(entity.getSellAmount())
                .totalFee(zero(entity.getAllocatedBuyFee()).add(zero(entity.getSellFee())))
                .grossProfit(entity.getGrossProfit())
                .netProfit(entity.getNetProfit())
                .grossReturnRate(entity.getGrossReturnRate())
                .returnRate(entity.getReturnRate())
                .holdingDays(entity.getHoldingDays())
                .dailyReturnRate(entity.getDailyReturnRate())
                .grossDailyReturnRate(calculateDailyReturnRate(entity.getGrossReturnRate(), entity.getHoldingDays()))
                .build();
    }

    private BigDecimal calculateDailyReturnRate(BigDecimal returnRate, BigDecimal holdingDays) {
        double effectiveDays = Math.max(1.0D, zero(holdingDays).doubleValue());
        double compoundBase = 1.0D + zero(returnRate).doubleValue();
        if (compoundBase <= 0.0D) {
            return BigDecimal.valueOf(-1).setScale(RATE_SCALE);
        }
        return BigDecimal.valueOf(Math.pow(compoundBase, 1.0D / effectiveDays) - 1.0D)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal zero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Pattern containsPattern(String value) {
        return Pattern.compile(Pattern.quote(value.trim()), Pattern.CASE_INSENSITIVE);
    }

    private record QueryContext(int current, int pageSize) {
    }
}
// AI_GENERATE_END-----------
