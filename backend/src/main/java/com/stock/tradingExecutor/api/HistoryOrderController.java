package com.stock.tradingExecutor.api;

import com.stock.tradingExecutor.domain.entity.HistoryOrder;
import com.stock.tradingExecutor.dto.HistoryOrderDTO;
import com.stock.tradingExecutor.persistence.HistoryOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/history-orders")
@RequiredArgsConstructor
public class HistoryOrderController {

    private final HistoryOrderRepository historyOrderRepository;

    @GetMapping
    public List<HistoryOrderDTO> list(
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String stockName,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @PageableDefault(size = 20, sort = "orderSubmitTime", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("分页查询历史订单: stockCode={}, stockName={}, direction={}, startDate={}, endDate={}, page={}",
                stockCode, stockName, direction, startDate, endDate, pageable.getPageNumber());

        Page<HistoryOrder> page = historyOrderRepository.findAll(pageable);
        return page.getContent().stream().map(this::toDTO).toList();
    }

    @GetMapping("/page")
    public Page<HistoryOrderDTO> page(
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String stockName,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("分页查询历史订单: stockCode={}, stockName={}, direction={}, startDate={}, endDate={}, page={}, size={}",
                stockCode, stockName, direction, startDate, endDate, page, size);

        Sort sort = Sort.by(Sort.Direction.DESC, "orderSubmitTime");
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<HistoryOrder> result = historyOrderRepository.findAll(pageable);

        return result.map(this::toDTO);
    }

    @GetMapping("/{id}")
    public HistoryOrderDTO getById(@PathVariable Long id) {
        log.info("查询历史订单详情: id={}", id);
        return historyOrderRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }

    @GetMapping("/count")
    public long count() {
        long total = historyOrderRepository.count();
        log.info("查询历史订单总数: {}", total);
        return total;
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
