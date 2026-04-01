package com.stock.tradingExecutor.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.modelService.domain.dto.PageResult;
import com.stock.tradingExecutor.domain.entity.HistoryOrder;
import com.stock.tradingExecutor.dto.HistoryOrderDTO;
import com.stock.tradingExecutor.dto.HistoryOrderPageRequest;
import com.stock.tradingExecutor.persistence.HistoryOrderRepository;
import com.stock.tradingExecutor.service.HistoryOrderSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 历史订单查询接口
 *
 * @author mwangli
 * @since 2026-04-01
 */
@Slf4j
@RestController
@RequestMapping("/api/historyOrders")
@RequiredArgsConstructor
public class HistoryOrderController {

    private final HistoryOrderRepository historyOrderRepository;
    private final HistoryOrderSyncService historyOrderSyncService;

    /**
     * 分页查询历史订单（支持多条件过滤）
     *
     * @param request 查询条件
     * @return 统一封装响应
     */
    @GetMapping("/page")
    public ResponseDTO<PageResult<HistoryOrderDTO>> page(HistoryOrderPageRequest request) {
        log.info("分页查询历史订单: stockCode={}, stockName={}, direction={}, startDate={}, endDate={}, page={}, size={}",
                request.getStockCode(), request.getStockName(), request.getDirection(),
                request.getStartDate(), request.getEndDate(), request.getPage(), request.getSize());

        Sort sort = Sort.by(Sort.Direction.DESC, "orderSubmitTime");
        Pageable pageable = PageRequest.of(
                request.getPage() != null ? request.getPage() : 0,
                request.getSize() != null ? request.getSize() : 20,
                sort
        );

        Page<HistoryOrder> page = historyOrderRepository.findByConditions(
                request.getStockCode(),
                request.getStockName(),
                request.getDirection(),
                request.getStartDate(),
                request.getEndDate(),
                pageable
        );

        List<HistoryOrderDTO> dtoList = page.getContent().stream().map(this::toDTO).toList();

        PageResult<HistoryOrderDTO> pageResult = PageResult.<HistoryOrderDTO>builder()
                .list(dtoList)
                .total(page.getTotalElements())
                .build();

        log.info("查询完成: 共 {} 条记录", page.getTotalElements());
        return ResponseDTO.success(pageResult);
    }

    /**
     * 根据ID查询历史订单详情
     *
     * @param id 订单ID
     * @return 统一封装响应
     */
    @GetMapping("/{id}")
    public ResponseDTO<HistoryOrderDTO> getById(@PathVariable Long id) {
        log.info("查询历史订单详情: id={}", id);
        return historyOrderRepository.findById(id)
                .map(order -> ResponseDTO.success(toDTO(order)))
                .orElse(ResponseDTO.error("订单不存在"));
    }

    /**
     * 查询历史订单总数
     *
     * @return 统一封装响应
     */
    @GetMapping("/count")
    public ResponseDTO<Long> count() {
        long total = historyOrderRepository.count();
        log.info("查询历史订单总数: {}", total);
        return ResponseDTO.success(total);
    }

    /**
     * 手动触发历史订单全量同步
     * 从2023年1月开始同步到当前月份
     *
     * @return 统一封装响应
     */
    @PostMapping("/sync")
    public ResponseDTO<HistoryOrderSyncService.SyncResult> triggerSync() {
        log.info("手动触发历史订单全量同步");

        try {
            HistoryOrderSyncService.SyncResult result = historyOrderSyncService.syncAllHistoryOrders();

            log.info("手动同步完成: 总获取={}, 新增={}, 重复={}, 失败={}, 批次={}, 耗时={}ms",
                    result.totalFetched(),
                    result.savedCount(),
                    result.duplicateCount(),
                    result.failedCount(),
                    result.syncBatchNo(),
                    result.costTimeMs());

            return ResponseDTO.success(result, "同步完成");
        } catch (Exception e) {
            log.error("手动同步失败: {}", e.getMessage(), e);
            return ResponseDTO.error("同步失败: " + e.getMessage());
        }
    }

    private HistoryOrderDTO toDTO(HistoryOrder entity) {
        HistoryOrderDTO dto = new HistoryOrderDTO();
        dto.setId(entity.getId());
        dto.setOrderDate(entity.getOrderDate());
        dto.setOrderNo(entity.getOrderNo());
        dto.setMarketType(entity.getMarketType());
        dto.setStockAccount(entity.getStockAccount());
        dto.setStockCode(entity.getStockCode());
        dto.setStockName(entity.getStockName());
        dto.setDirection(entity.getDirection());
        dto.setPrice(entity.getPrice());
        dto.setQuantity(entity.getQuantity());
        dto.setAmount(entity.getAmount());
        dto.setSerialNo(entity.getSerialNo());
        dto.setOrderTime(entity.getOrderTime());
        dto.setRemark(entity.getRemark());
        dto.setFullName(entity.getFullName());
        dto.setSyncBatchNo(entity.getSyncBatchNo());
        dto.setLastSyncTime(entity.getLastSyncTime());
        dto.setOrderSubmitTime(entity.getOrderSubmitTime());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}
