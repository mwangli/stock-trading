package com.stock.tradingExecutor.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.modelService.domain.dto.PageResult;
import com.stock.tradingExecutor.domain.entity.TradeRecord;
import com.stock.tradingExecutor.persistence.TradeRecordRepository;
import com.stock.tradingExecutor.service.TradeRecordAssembleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

/**
 * 交易记录接口
 *
 * @author mwangli
 * @since 2026-04-01
 */
@Slf4j
@RestController
@RequestMapping("/api/tradeRecords")
@RequiredArgsConstructor
public class TradeRecordController {

    private final TradeRecordRepository tradeRecordRepository;
    private final TradeRecordAssembleService tradeRecordAssembleService;

    /**
     * 分页查询交易记录
     */
    @GetMapping("/page")
    public ResponseDTO<PageResult<TradeRecord>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String stockCode) {
        log.info("分页查询交易记录: current={}, pageSize={}, stockCode={}", current, pageSize, stockCode);

        Sort sort = Sort.by(Sort.Direction.DESC, "buyTime");
        Pageable pageable = PageRequest.of(current - 1, pageSize, sort);

        Page<TradeRecord> page;
        if (stockCode != null && !stockCode.isEmpty()) {
            page = tradeRecordRepository.findByStockCode(stockCode, pageable);
        } else {
            page = tradeRecordRepository.findAll(pageable);
        }

        PageResult<TradeRecord> pageResult = PageResult.<TradeRecord>builder()
                .list(page.getContent())
                .total(page.getTotalElements())
                .build();

        log.info("查询完成: 共 {} 条记录", page.getTotalElements());
        return ResponseDTO.success(pageResult);
    }

    /**
     * 根据交易ID查询交易记录详情
     */
    @GetMapping("/{tradeId}")
    public ResponseDTO<TradeRecord> getByTradeId(@PathVariable String tradeId) {
        log.info("查询交易记录详情: tradeId={}", tradeId);
        return tradeRecordRepository.findByTradeId(tradeId)
                .map(tradeRecord -> ResponseDTO.success(tradeRecord))
                .orElse(ResponseDTO.error("交易记录不存在"));
    }

    /**
     * 重新组装交易记录
     */
    @PostMapping("/reassemble")
    public ResponseDTO<Void> reassemble() {
        log.info("重新组装交易记录");
        try {
            tradeRecordAssembleService.assembleTradeRecords();
            return ResponseDTO.success(null, "重新组装成功");
        } catch (Exception e) {
            log.error("重新组装失败: {}", e.getMessage(), e);
            return ResponseDTO.error("重新组装失败: " + e.getMessage());
        }
    }
}
