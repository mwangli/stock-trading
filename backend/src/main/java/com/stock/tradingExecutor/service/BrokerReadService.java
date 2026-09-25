// AI_GENERATE_START ---
package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.domain.dto.BrokerAccountDto;
import com.stock.tradingExecutor.domain.dto.BrokerFillDto;
import com.stock.tradingExecutor.domain.dto.BrokerFillListResponseDto;
import com.stock.tradingExecutor.domain.dto.BrokerHistoryQueryRequest;
import com.stock.tradingExecutor.domain.dto.BrokerOrderDto;
import com.stock.tradingExecutor.domain.dto.BrokerOrderListResponseDto;
import com.stock.tradingExecutor.domain.dto.BrokerPositionDto;
import com.stock.tradingExecutor.domain.dto.BrokerPositionListResponseDto;
import com.stock.tradingExecutor.domain.dto.BrokerReadStatusDto;
import com.stock.tradingExecutor.domain.entity.Position;
import com.stock.tradingExecutor.domain.vo.AccountStatus;
import com.stock.tradingExecutor.domain.vo.BrokerFillSnapshot;
import com.stock.tradingExecutor.domain.vo.BrokerOrderSnapshot;
import com.stock.tradingExecutor.execution.BrokerAdapter;
import com.stock.tradingExecutor.execution.TradingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private final TradingProperties tradingProperties;

    /**
     * 获取券商只读网关状态。
     *
     * @return 当前券商、模式和门禁状态
     */
    public BrokerReadStatusDto getStatus() {
        return BrokerReadStatusDto.builder()
                .brokerName(brokerAdapter.getName())
                .tradingMode(tradingProperties.getMode().name())
                .authenticated(brokerAdapter.isAuthenticated())
                .realWriteEnabled(tradingProperties.isRealWriteEnabled())
                .liveWriteAllowed(tradingProperties.isLiveWriteAllowed())
                .build();
    }

    /**
     * 查询账户资金。
     *
     * @return 账户资金快照
     */
    public BrokerAccountDto getAccount() {
        ensureAuthenticated();
        AccountStatus account = brokerAdapter.getAccountInfo();
        if (account == null) {
            throw new IllegalStateException("未获得有效账户资金，请检查券商启用状态和登录会话");
        }
        return BrokerAccountDto.builder()
                .totalAssets(account.getTotalAssets())
                .availableCash(account.getAvailableCash())
                .frozenAmount(account.getFrozenAmount())
                .totalPosition(account.getTotalPosition())
                .capturedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 查询当前持仓。
     *
     * @return 持仓列表
     */
    public BrokerPositionListResponseDto getPositions() {
        ensureAuthenticated();
        List<BrokerPositionDto> items = brokerAdapter.getPositions().stream()
                .map(this::toPositionDto)
                .toList();
        return BrokerPositionListResponseDto.builder()
                .items(items)
                .total(items.size())
                .capturedAt(LocalDateTime.now())
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
        return buildOrderResponse(
                brokerAdapter.getHistoryOrderSnapshots(request.getStartDate(), request.getEndDate()),
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
        if (!brokerAdapter.isAuthenticated()) {
            throw new IllegalStateException("券商会话无效，请先完成登录");
        }
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

    private BrokerPositionDto toPositionDto(Position position) {
        return BrokerPositionDto.builder()
                .stockCode(position.getStockCode())
                .stockName(position.getStockName())
                .totalQuantity(position.getQuantity())
                .availableQuantity(position.getAvailableQuantity())
                .frozenQuantity(position.getFrozenQuantity())
                .averageCost(position.getAvgCost())
                .currentPrice(position.getCurrentPrice())
                .marketValue(position.getMarketValue())
                .market(position.getMarket())
                .shareholderAccountMasked(maskAccount(position.getShareholderAccount()))
                .build();
    }

    private BrokerOrderDto toOrderDto(BrokerOrderSnapshot snapshot) {
        return BrokerOrderDto.builder()
                .orderId(snapshot.getOrderId())
                .stockCode(snapshot.getStockCode())
                .stockName(snapshot.getStockName())
                .direction(snapshot.getDirection())
                .orderPrice(snapshot.getOrderPrice())
                .orderQuantity(snapshot.getOrderQuantity())
                .filledQuantity(snapshot.getFilledQuantity())
                .averageFillPrice(snapshot.getAverageFillPrice())
                .status(snapshot.getStatus() != null ? snapshot.getStatus().getCode() : "UNKNOWN")
                .rawStatus(snapshot.getRawStatus())
                .shareholderAccountMasked(maskAccount(snapshot.getShareholderAccount()))
                .market(snapshot.getMarket())
                .orderTime(snapshot.getOrderTime())
                .build();
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
// AI_GENERATE_END ---
