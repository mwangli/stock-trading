// AI_GENERATE_START---------------
package com.stock.trading.service;

import com.stock.trading.dto.BrokerAccountDto;
import com.stock.trading.dto.BrokerFillDto;
import com.stock.trading.dto.BrokerFillListResponseDto;
import com.stock.trading.dto.BrokerHistoryQueryRequest;
import com.stock.trading.dto.BrokerOrderDto;
import com.stock.trading.dto.BrokerOrderListResponseDto;
import com.stock.trading.dto.BrokerReadStatusDto;
import com.stock.trading.entity.AccountSnapshotEntity;
import com.stock.trading.entity.OrderStatus;
import com.stock.trading.vo.BrokerFillSnapshot;
import com.stock.trading.vo.BrokerOrderSnapshot;
import com.stock.trading.execution.BrokerAdapter;
import com.stock.trading.execution.FeeCalculator;
import com.stock.trading.execution.TradingProperties;
import com.stock.trading.repository.AccountSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 券商只读查询服务。
 * 负责日期范围校验、敏感字段脱敏和协议对象到接口 DTO 的转换。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class BrokerReadService {

    private static final int MAX_HISTORY_DAYS = 31;

    private final BrokerAdapter brokerAdapter;
    private final FeeCalculator feeCalculator;
    private final TradingProperties tradingProperties;
    private final BrokerIdentityService brokerIdentityService;
    private final AccountSnapshotRepository accountSnapshotRepository;

    /**
     * 获取券商只读网关状态。
     *
     * @return 当前券商、模式和门禁状态
     */
    public BrokerReadStatusDto getStatus() {
        return BrokerReadStatusDto.builder()
                .brokerName(brokerAdapter.getName())
                .tradingMode("AUTOMATIC")
                .authenticated(brokerAdapter.isAuthenticated())
                .realWriteEnabled(tradingProperties.isRealWriteEnabled())
                .liveWriteAllowed(tradingProperties.isLiveWriteAllowed())
                .build();
    }

    /**
     * 从数据库查询定时同步得到的最新账户资金快照。
     *
     * @return 账户资金快照
     */
    public BrokerAccountDto getAccount() {
        AccountSnapshotEntity account = accountSnapshotRepository
                .findByBrokerCodeAndAccountId(
                        brokerIdentityService.getBrokerCode(), brokerIdentityService.getAccountId())
                .orElseThrow(() -> new IllegalStateException("暂无账户资金快照，请等待券商定时同步任务执行"));
        return toAccountDto(account);
    }

    /**
     * 查询指定信息截止时间可使用的当前账户资金快照。
     * 系统只保留最新快照；当最新快照晚于截止时间时，不使用未来账户事实。
     *
     * @param informationCutoffAt 信息截止时间，包含该时点
     * @return 截止时点可使用的当前账户资金快照
     * @throws IllegalArgumentException 截止时间为空时抛出
     * @throws IllegalStateException 截止时点前没有账户快照时抛出
     */
    public BrokerAccountDto getAccountAtOrBefore(LocalDateTime informationCutoffAt) {
        if (informationCutoffAt == null) {
            throw new IllegalArgumentException("账户快照截止时间不能为空");
        }
        AccountSnapshotEntity account = accountSnapshotRepository
                .findByBrokerCodeAndAccountId(
                        brokerIdentityService.getBrokerCode(), brokerIdentityService.getAccountId())
                .filter(snapshot -> snapshot.getCapturedAt() != null
                        && !snapshot.getCapturedAt().isAfter(informationCutoffAt))
                .orElseThrow(() -> new IllegalStateException("信息截止时间前无可用账户资金快照"));
        return toAccountDto(account);
    }

    private BrokerAccountDto toAccountDto(AccountSnapshotEntity account) {
        return BrokerAccountDto.builder()
                .totalAssets(account.getTotalAssets())
                .availableCash(account.getAvailableCash())
                .frozenAmount(account.getFrozenCash())
                .totalPosition(account.getMarketValue())
                .capturedAt(account.getCapturedAt())
                .build();
    }

    /**
     * 查询当日全部委托。
     *
     * @return 当日委托列表
     */
    public BrokerOrderListResponseDto getTodayOrders() {
        ensureAuthenticated();
        return buildOrderResponse(brokerAdapter.getTodayOrderSnapshots(), null, null);
    }

    /**
     * 查询当日成交。
     *
     * @return 当日成交列表
     */
    public BrokerFillListResponseDto getTodayFills() {
        ensureAuthenticated();
        return buildFillResponse(brokerAdapter.getTodayFillSnapshots(), null, null);
    }

    /**
     * 查询历史委托。
     *
     * @param request 日期范围
     * @return 历史委托列表
     */
    public BrokerOrderListResponseDto getHistoryOrders(BrokerHistoryQueryRequest request) {
        ensureAuthenticated();
        validateHistoryRange(request);
        List<BrokerOrderSnapshot> filledOrders = brokerAdapter
                .getHistoryOrderSnapshots(request.getStartDate(), request.getEndDate()).stream()
                .filter(snapshot -> snapshot.getStatus() == OrderStatus.FILLED)
                .toList();
        return buildOrderResponse(
                filledOrders,
                request.getStartDate(), request.getEndDate());
    }

    /**
     * 查询历史成交。
     *
     * @param request 日期范围
     * @return 历史成交列表
     */
    public BrokerFillListResponseDto getHistoryFills(BrokerHistoryQueryRequest request) {
        ensureAuthenticated();
        validateHistoryRange(request);
        return buildFillResponse(
                brokerAdapter.getHistoryFillSnapshots(request.getStartDate(), request.getEndDate()),
                request.getStartDate(), request.getEndDate());
    }

    private void validateHistoryRange(BrokerHistoryQueryRequest request) {
        if (request == null || request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("开始日期和结束日期不能为空");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
        if (days > MAX_HISTORY_DAYS) {
            throw new IllegalArgumentException("单次历史查询不能超过31个自然日");
        }
    }

    private void ensureAuthenticated() {
        brokerAdapter.ensureAuthenticated();
    }

    private BrokerOrderListResponseDto buildOrderResponse(List<BrokerOrderSnapshot> snapshots,
                                                           LocalDate startDate, LocalDate endDate) {
        List<BrokerOrderDto> items = snapshots.stream().map(this::toOrderDto).toList();
        return BrokerOrderListResponseDto.builder()
                .items(items)
                .total(items.size())
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    private BrokerFillListResponseDto buildFillResponse(List<BrokerFillSnapshot> snapshots,
                                                         LocalDate startDate, LocalDate endDate) {
        List<BrokerFillDto> items = snapshots.stream().map(this::toFillDto).toList();
        return BrokerFillListResponseDto.builder()
                .items(items)
                .total(items.size())
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    private BrokerOrderDto toOrderDto(BrokerOrderSnapshot snapshot) {
        BigDecimal transactionAmount = calculateTransactionAmount(snapshot);
        return BrokerOrderDto.builder()
                .orderId(snapshot.getOrderId())
                .stockCode(snapshot.getStockCode())
                .stockName(snapshot.getStockName())
                .direction(snapshot.getDirection())
                .orderPrice(snapshot.getOrderPrice())
                .orderQuantity(snapshot.getOrderQuantity())
                .filledQuantity(snapshot.getFilledQuantity())
                .averageFillPrice(snapshot.getAverageFillPrice())
                .transactionAmount(transactionAmount)
                .commission(feeCalculator.calculate(transactionAmount))
                .status(snapshot.getStatus() != null ? snapshot.getStatus().getCode() : "UNKNOWN")
                .rawStatus(snapshot.getRawStatus())
                .shareholderAccountMasked(maskAccount(snapshot.getShareholderAccount()))
                .market(snapshot.getMarket())
                .orderTime(snapshot.getOrderTime())
                .build();
    }

    private BigDecimal calculateTransactionAmount(BrokerOrderSnapshot snapshot) {
        if (snapshot.getAverageFillPrice() == null
                || snapshot.getFilledQuantity() == null
                || snapshot.getFilledQuantity() <= 0) {
            return BigDecimal.ZERO;
        }
        return snapshot.getAverageFillPrice()
                .multiply(BigDecimal.valueOf(snapshot.getFilledQuantity()));
    }

    private BrokerFillDto toFillDto(BrokerFillSnapshot snapshot) {
        return BrokerFillDto.builder()
                .fillId(snapshot.getFillId())
                .orderId(snapshot.getOrderId())
                .stockCode(snapshot.getStockCode())
                .stockName(snapshot.getStockName())
                .direction(snapshot.getDirection())
                .fillPrice(snapshot.getFillPrice())
                .fillQuantity(snapshot.getFillQuantity())
                .fillAmount(snapshot.getFillAmount())
                .fillTime(snapshot.getFillTime())
                .build();
    }

    private String maskAccount(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        String trimmed = account.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
}
// AI_GENERATE_END---------------
