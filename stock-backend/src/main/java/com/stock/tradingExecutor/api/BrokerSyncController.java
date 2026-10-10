// AI_GENERATE_START --
package com.stock.tradingExecutor.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.tradingExecutor.domain.dto.BrokerHistoryQueryRequest;
import com.stock.tradingExecutor.domain.dto.BrokerSyncResultDto;
import com.stock.tradingExecutor.service.BrokerDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 券商只读事实同步接口。
 * 仅触发账户、持仓、委托和成交读取落库，不包含任何券商写操作。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@RestController
@RequestMapping("/api/broker-sync")
@RequiredArgsConstructor
public class BrokerSyncController {

    private final BrokerDataSyncService brokerDataSyncService;

    /**
     * 手动同步当前账户、持仓、当日委托和成交。
     *
     * @return 当前事实同步结果
     */
    @PostMapping("/current")
    public ResponseDTO<BrokerSyncResultDto> syncCurrentFacts() {
        log.info("手动同步券商当前账户、持仓、委托和成交事实");
        return executeSync(brokerDataSyncService::syncCurrentFacts);
    }

    /**
     * 手动同步最多 31 个自然日的历史委托和成交。
     *
     * @param request 历史日期范围
     * @return 历史窗口同步结果
     */
    @PostMapping("/history-window")
    public ResponseDTO<BrokerSyncResultDto> syncHistoryWindow(
            @RequestBody BrokerHistoryQueryRequest request) {
        log.info("手动同步券商历史事实: startDate={}, endDate={}",
                request != null ? request.getStartDate() : null,
                request != null ? request.getEndDate() : null);
        return executeSync(() -> brokerDataSyncService.syncHistoryWindow(request));
    }

    private ResponseDTO<BrokerSyncResultDto> executeSync(SyncAction action) {
        try {
            BrokerSyncResultDto result = action.execute();
            return result.isSuccess()
                    ? ResponseDTO.success(result)
                    : ResponseDTO.<BrokerSyncResultDto>builder()
                            .success(false)
                            .message(result.getMessage())
                            .data(result)
                            .build();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("券商只读事实同步未执行: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("券商只读事实同步异常", exception);
            return ResponseDTO.error("券商只读事实同步失败，请检查服务日志和同步批次");
        }
    }

    @FunctionalInterface
    private interface SyncAction {
        BrokerSyncResultDto execute();
    }
}
// AI_GENERATE_END --
