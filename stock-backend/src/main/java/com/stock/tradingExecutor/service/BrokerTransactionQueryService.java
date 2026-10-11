// AI_GENERATE_START -----
package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.domain.dto.BrokerOrderDto;
import com.stock.tradingExecutor.domain.dto.BrokerOrderPageResponseDto;
import com.stock.tradingExecutor.domain.dto.BrokerTransactionQueryRequest;
import com.stock.tradingExecutor.domain.dto.TradeRecordDto;
import com.stock.tradingExecutor.domain.dto.TradeRecordPageResponseDto;
import com.stock.tradingExecutor.domain.entity.BrokerOrderEntity;
import com.stock.tradingExecutor.domain.entity.TradeRecordEntity;
import com.stock.tradingExecutor.persistence.BrokerOrderRepository;
import com.stock.tradingExecutor.persistence.TradeRecordRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    private final BrokerOrderRepository brokerOrderRepository;
    private final TradeRecordRepository tradeRecordRepository;
    private final BrokerIdentityService brokerIdentityService;

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
                        Sort.by(
                                Sort.Order.desc("submittedAt"),
                                Sort.Order.desc("id"))));
        return BrokerOrderPageResponseDto.builder()
                .items(page.getContent().stream().map(this::toOrderDto).toList())
                .total(page.getTotalElements())
                .current(context.current())
                .pageSize(context.pageSize())
                .build();
    }

    /**
     * 分页查询当前账户由历史订单汇总得到的交易记录。
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
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("brokerCode"), brokerIdentityService.getBrokerCode()));
            predicates.add(criteriaBuilder.equal(root.get("accountId"), brokerIdentityService.getAccountId()));
            predicates.add(criteriaBuilder.equal(root.get("status"), "FILLED"));
            predicates.add(criteriaBuilder.greaterThan(root.get("filledQuantity"), 0));
            predicates.add(criteriaBuilder.isNotNull(root.get("averageFillPrice")));
            predicates.add(criteriaBuilder.greaterThan(root.get("averageFillPrice"), java.math.BigDecimal.ZERO));
            addDirectionPredicate(request, predicates, criteriaBuilder, root.get("side"));
            if (hasText(request.getKeyword())) {
                String pattern = likePattern(request.getKeyword());
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("stockCode")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("stockName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("brokerOrderNo")), pattern)));
            }
            if (request.getStartDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("tradeDate"), request.getStartDate()));
            }
            if (request.getEndDate() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("tradeDate"), request.getEndDate()));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<TradeRecordEntity> buildTradeSpecification(BrokerTransactionQueryRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("brokerCode"), brokerIdentityService.getBrokerCode()));
            predicates.add(criteriaBuilder.equal(root.get("accountId"), brokerIdentityService.getAccountId()));
            if (hasText(request.getKeyword())) {
                String pattern = likePattern(request.getKeyword());
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("stockCode")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("stockName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("buyOrderNo")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("sellOrderNos")), pattern)));
            }
            if (request.getStartDate() != null) {
                LocalDateTime startTime = request.getStartDate().atStartOfDay();
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("buyTime"), startTime));
            }
            if (request.getEndDate() != null) {
                LocalDateTime exclusiveEnd = request.getEndDate().plusDays(1).atStartOfDay();
                predicates.add(criteriaBuilder.lessThan(root.get("buyTime"), exclusiveEnd));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addDirectionPredicate(BrokerTransactionQueryRequest request, List<Predicate> predicates,
                                       jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                       jakarta.persistence.criteria.Path<String> side) {
        if (hasText(request.getDirection())) {
            predicates.add(criteriaBuilder.equal(side, request.getDirection().trim().toUpperCase(Locale.ROOT)));
        }
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
                .grossProfit(entity.getGrossProfit())
                .netProfit(entity.getNetProfit())
                .grossReturnRate(entity.getGrossReturnRate())
                .returnRate(entity.getReturnRate())
                .holdingDays(entity.getHoldingDays())
                .dailyReturnRate(entity.getDailyReturnRate())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String likePattern(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private record QueryContext(int current, int pageSize) {
    }
}
// AI_GENERATE_END -----
