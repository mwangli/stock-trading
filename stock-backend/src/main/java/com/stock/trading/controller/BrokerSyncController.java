// AI_GENERATE_START---------
package com.stock.trading.controller;

import com.stock.base.api.ResponseDTO;
import com.stock.trading.dto.BrokerHistoryQueryRequest;
import com.stock.trading.dto.BrokerSyncResultDto;
import com.stock.trading.service.BrokerDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 券商只读事实同步接口。
 * 仅触发账户、持仓和已成交委托读取落库，不包含任何券商写操作。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@RestController
@RequestMapping("/api/brokerSync")
@RequiredArgsConstructor
public class BrokerSyncController {

    private final BrokerDataSyncService brokerDataSyncService;

    /**
     * 手动同步当前账户、持仓和当日已成交委托。
     *
     * @return 当前事实同步结果
     */
    @PostMapping("/current")
    public ResponseDTO<BrokerSyncResultDto> syncCurrentFacts() {
        log.info("手动同步券商当前账户、持仓和已成交委托");
        return executeSync(brokerDataSyncService::syncCurrentFacts);
    }

    /**
     * 手动同步最多 31 个自然日的已成交历史委托。
     *
     * @param request 历史日期范围
     * @return 历史窗口同步结果
     */
    @PostMapping("/historyWindow")
    public ResponseDTO<BrokerSyncResultDto> syncHistoryWindow(
            @RequestBody BrokerHistoryQueryRequest request) {
        log.info("手动同步券商已成交历史委托: startDate={}, endDate={}",
                request != null ? request.getStartDate() : null,
                request != null ? request.getEndDate() : null);
        return executeSync(() -> brokerDataSyncService.syncHistoryWindow(request));
    }

    /**
     * 手动按月同步最近三年的已成交历史委托。
     *
     * 已成功窗口会跳过，失败或部分成功窗口会继续重试，
     * 整个过程仅调用券商只读查询并执行本地数据库幂等写入。
     *
     * @return 最近三年历史同步汇总结果
     */
    @PostMapping("/historyBackfill")
    public ResponseDTO<BrokerSyncResultDto> syncHistoryBackfill() {
        log.info("手动按月同步最近三年券商已成交历史委托");
        return executeSync(brokerDataSyncService::syncHistoryBackfill);
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
// AI_GENERATE_END---------
