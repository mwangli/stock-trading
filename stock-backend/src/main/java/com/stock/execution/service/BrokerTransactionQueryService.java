// AI_GENERATE_START-------------
package com.stock.execution.service;

import com.stock.execution.dto.BrokerOrderDto;
import com.stock.execution.dto.BrokerOrderPageResponseDto;
import com.stock.execution.dto.BrokerTransactionQueryRequest;
import com.stock.execution.dto.TradeRecordDto;
import com.stock.execution.dto.TradeRecordPageResponseDto;
import com.stock.execution.entity.BrokerOrderEntity;
import com.stock.execution.entity.TradeRecordEntity;
import com.stock.execution.repository.BrokerOrderRepository;
import com.stock.execution.repository.TradeRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

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
    private final BrokerOrderRepository brokerOrderRepository;
    private final TradeRecordRepository tradeRecordRepository;

    /**
     * 分页查询当前账户已入库的已成交历史订单。
     *
     * @param request 查询条件
     * @return 历史订单分页结果
     */
    public BrokerOrderPageResponseDto queryOrders(BrokerTransactionQueryRequest request) {
        QueryContext context = validate(request);
        Page<BrokerOrderEntity> page = brokerOrderRepository.findAll(
                buildOrderSpecification(request),
                PageRequest.of(context.current() - 1, context.pageSize(),
                        Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.desc("id"))));
        return BrokerOrderPageResponseDto.builder()
                .items(page.getContent().stream().map(this::toOrderDto).toList())
                .total(page.getTotalElements())
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
        Page<TradeRecordEntity> page = tradeRecordRepository.findAll(
                buildTradeSpecification(request),
                PageRequest.of(context.current() - 1, context.pageSize(),
                        Sort.by(Sort.Direction.DESC, "buyTime")));
        return TradeRecordPageResponseDto.builder()
                .items(page.getContent().stream().map(this::toTradeRecordDto).toList())
                .total(page.getTotalElements())
                .current(context.current())
                .pageSize(context.pageSize())
                .build();
    }

    private Specification<BrokerOrderEntity> buildOrderSpecification(BrokerTransactionQueryRequest request) {
        Specification<BrokerOrderEntity> specification = (root, query, builder) -> builder.and(
                builder.equal(root.get("brokerCode"), brokerIdentityService.getBrokerCode()),
                builder.equal(root.get("accountId"), brokerIdentityService.getAccountId()),
                builder.equal(root.get("status"), "FILLED"),
                builder.greaterThan(root.<Integer>get("filledQuantity"), 0),
                builder.greaterThan(root.<BigDecimal>get("averageFillPrice"), BigDecimal.ZERO));
        if (hasText(request.getDirection())) {
            String direction = request.getDirection().trim().toUpperCase(Locale.ROOT);
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("side"), direction));
        }
        if (hasText(request.getKeyword())) {
            String pattern = containsLikePattern(request.getKeyword());
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.<String>get("stockCode")), pattern, '\\'),
                    builder.like(builder.lower(root.<String>get("stockName")), pattern, '\\'),
                    builder.like(builder.lower(root.<String>get("brokerOrderNo")), pattern, '\\')));
        }
        if (request.getStartDate() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.<java.time.LocalDate>get("tradeDate"), request.getStartDate()));
        }
        if (request.getEndDate() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.<java.time.LocalDate>get("tradeDate"), request.getEndDate()));
        }
        return specification;
    }

    private Specification<TradeRecordEntity> buildTradeSpecification(BrokerTransactionQueryRequest request) {
        Specification<TradeRecordEntity> specification = (root, query, builder) -> builder.and(
                builder.equal(root.get("brokerCode"), brokerIdentityService.getBrokerCode()),
                builder.equal(root.get("accountId"), brokerIdentityService.getAccountId()));
        if (hasText(request.getKeyword())) {
            String pattern = containsLikePattern(request.getKeyword());
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.<String>get("stockCode")), pattern, '\\'),
                    builder.like(builder.lower(root.<String>get("stockName")), pattern, '\\')));
        }
        if (request.getStartDate() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.<java.time.LocalDateTime>get("buyTime"),
                            request.getStartDate().atStartOfDay()));
        }
        if (request.getEndDate() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThan(root.<java.time.LocalDateTime>get("buyTime"),
                            request.getEndDate().plusDays(1).atStartOfDay()));
        }
        return specification;
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

    private String containsLikePattern(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + normalized + "%";
    }

    private record QueryContext(int current, int pageSize) {
    }
}
// AI_GENERATE_END-------------
