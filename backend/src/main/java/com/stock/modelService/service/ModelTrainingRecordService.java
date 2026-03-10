package com.stock.modelService.service;

import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.modelService.domain.dto.ModelTrainingRecordDto;
import com.stock.modelService.domain.dto.PageResult;
import com.stock.modelService.domain.entity.LstmModelDocument;
import com.stock.modelService.domain.entity.ModelTrainingRecord;
import com.stock.modelService.persistence.LstmModelRepository;
import com.stock.modelService.persistence.ModelTrainingRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型训练记录服务
 * <p>
 * 负责：
 * 1. 按股票代码同步生成模型训练记录（未训练/已训练）；
 * 2. 在训练完成后更新对应股票的训练状态和统计信息；
 * 3. 为前端提供分页查询 DTO。
 * </p>
 *
 * @author AI Assistant
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelTrainingRecordService {

    private final ModelTrainingRecordRepository recordRepository;
    private final StockInfoRepository stockInfoRepository;
    private final LstmModelRepository lstmModelRepository;

    /**
     * 同步所有股票的模型训练记录：
     * <ul>
     *     <li>先从 MySQL 查询所有训练记录（约 4000 条）；</li>
     *     <li>针对每一条记录，按股票代码从 Mongo 中查询最新的 LSTM 模型；</li>
     *     <li>根据查询结果更新该记录的训练状态与统计信息，并打印详细日志。</li>
     * </ul>
     */
    @Transactional
    public void syncAllStocks() {
        long start = System.currentTimeMillis();
        log.info("[ModelTrainingRecord] 开始同步模型训练记录状态...");

        List<ModelTrainingRecord> records = recordRepository.findAll();
        if (records.isEmpty()) {
            log.warn("[ModelTrainingRecord] 未找到任何训练记录，跳过同步");
            return;
        }
        log.info("[ModelTrainingRecord] 当前训练记录总数: {}", records.size());

        List<ModelTrainingRecord> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (ModelTrainingRecord record : records) {
            String code = record.getStockCode();
            if (code == null || code.trim().isEmpty()) {
                log.warn("[ModelTrainingRecordSync] 跳过无效记录, id={}, stockCode 为空", record.getId());
                continue;
            }
            String trimmedCode = code.trim();
            try {
                LstmModelDocument latest = lstmModelRepository.findTopByModelNameOrderByCreatedAtDesc(trimmedCode);
                if (latest == null) {
                    log.info("[ModelTrainingRecordSync] code={} 未找到对应的 Mongo 模型文档，保持原有状态", trimmedCode);
                    continue;
                }

                // 更新训练状态与统计信息
                record.setTrained(Boolean.TRUE);
                record.setTraining(Boolean.FALSE);
                record.setLastTrainTime(latest.getCreatedAt());
                record.setLastEpochs(latest.getEpoch());
                record.setLastTrainLoss(latest.getTrainLoss());
                record.setLastValLoss(latest.getValLoss());
                record.setLastModelId(latest.getId());
                record.setUpdatedAt(now);

                toSave.add(record);

                log.info(
                        "[ModelTrainingRecordSync] 更新记录 | id={}, code={}, lastTrainTime={}, epochs={}, trainLoss={}, valLoss={}, modelId={}",
                        record.getId(),
                        trimmedCode,
                        latest.getCreatedAt(),
                        latest.getEpoch(),
                        latest.getTrainLoss(),
                        latest.getValLoss(),
                        latest.getId()
                );
            } catch (Exception e) {
                log.error("[ModelTrainingRecordSync] 从 Mongo 查询最新模型失败，跳过该记录 | id={}, code={}", record.getId(), trimmedCode, e);
            }
        }

        if (!toSave.isEmpty()) {
            recordRepository.saveAll(toSave);
            log.info("[ModelTrainingRecord] 同步完成，新增/更新记录数: {}", toSave.size());
        } else {
            log.info("[ModelTrainingRecord] 同步完成，本次无需要更新的记录");
        }

        long costMs = System.currentTimeMillis() - start;
        log.info("[ModelTrainingRecord] 同步任务结束，总耗时: {} ms (~{} s)", costMs, costMs / 1000);
    }

    /**
     * 在训练完成后更新对应股票的训练记录
     *
     * @param stockCodes        股票代码串，可为逗号分隔的多个代码
     * @param epochs            实际训练轮次
     * @param trainLoss         训练集损失
     * @param valLoss           验证集损失
     * @param durationSeconds   训练耗时（秒）
     * @param mongoModelId      MongoDB 模型 ID（不含前缀）
     */
    @Transactional
    public void updateAfterTraining(String stockCodes,
                                    int epochs,
                                    double trainLoss,
                                    double valLoss,
                                    long durationSeconds,
                                    String mongoModelId) {
        if (stockCodes == null || stockCodes.isBlank()) {
            return;
        }
        String[] codes = stockCodes.split(",");
        LocalDateTime now = LocalDateTime.now();

        for (String raw : codes) {
            String code = raw.trim();
            if (code.isEmpty()) {
                continue;
            }
            StockInfo stockInfo = stockInfoRepository.findByCode(code).orElse(null);
            ModelTrainingRecord record = recordRepository.findByStockCodeIn(Collections.singletonList(code))
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (record == null) {
                record = new ModelTrainingRecord();
                record.setStockCode(code);
                record.setCreatedAt(now);
            }
            if (stockInfo != null) {
                record.setStockName(stockInfo.getName());
            }
            record.setTrained(Boolean.TRUE);
            record.setTraining(Boolean.FALSE);
            record.setLastTrainTime(now);
            record.setLastDurationSeconds(durationSeconds);
            record.setLastEpochs(epochs);
            record.setLastTrainLoss(trainLoss);
            record.setLastValLoss(valLoss);
            record.setLastModelId(mongoModelId);
            record.setUpdatedAt(now);
            recordRepository.save(record);
        }
    }

    /**
     * 标记一批股票当前是否处于训练中
     *
     * @param stockCodes 股票代码串（逗号分隔）
     * @param training   true=训练中，false=训练结束
     */
    @Transactional
    public void markTraining(String stockCodes, boolean training) {
        if (stockCodes == null || stockCodes.isBlank()) {
            return;
        }
        String[] codes = stockCodes.split(",");
        LocalDateTime now = LocalDateTime.now();
        for (String raw : codes) {
            String code = raw.trim();
            if (code.isEmpty()) {
                continue;
            }
            StockInfo stockInfo = stockInfoRepository.findByCode(code).orElse(null);
            ModelTrainingRecord record = recordRepository.findByStockCodeIn(Collections.singletonList(code))
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (record == null) {
                record = new ModelTrainingRecord();
                record.setStockCode(code);
                record.setCreatedAt(now);
            }
            if (stockInfo != null) {
                record.setStockName(stockInfo.getName());
            }
            record.setTraining(training);
            record.setUpdatedAt(now);
            recordRepository.save(record);
        }
    }

    /**
     * 分页查询模型训练记录，返回前端 DTO
     *
     * @param keyword  股票代码或名称关键字
     * @param current  当前页，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ModelTrainingRecordDto> page(String keyword, int current, int pageSize) {
        if (current < 1) {
            current = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        if (pageSize > 200) {
            pageSize = 200;
        }

        Pageable pageable = PageRequest.of(current - 1, pageSize, Sort.by(Sort.Direction.DESC, "lastTrainTime", "stockCode"));
        Page<ModelTrainingRecord> page;
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            page = recordRepository.findByStockCodeContainingIgnoreCaseOrStockNameContainingIgnoreCase(kw, kw, pageable);
        } else {
            page = recordRepository.findAll(pageable);
        }

        List<ModelTrainingRecordDto> list = page.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PageResult.<ModelTrainingRecordDto>builder()
                .list(list)
                .total(page.getTotalElements())
                .build();
    }

    private ModelTrainingRecordDto toDto(ModelTrainingRecord record) {
        Integer epoch = record.getLastEpochs() != null ? record.getLastEpochs() : 0;

        return ModelTrainingRecordDto.builder()
                .id(record.getId())
                .stockCode(record.getStockCode())
                .stockName(record.getStockName())
                .trained(Boolean.TRUE.equals(record.getTrained()))
                .training(Boolean.TRUE.equals(record.getTraining()))
                .lastTrainTime(record.getLastTrainTime())
                .lastDurationSeconds(record.getLastDurationSeconds())
                .lastEpochs(record.getLastEpochs())
                .lastTrainLoss(record.getLastTrainLoss())
                .lastValLoss(record.getLastValLoss())
                .lastModelId(record.getLastModelId())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                // 兼容前端原有字段
                .modelName(record.getStockCode())
                .name(record.getStockName())
                .epoch(epoch)
                .modelVersion("v1")
                .profitAmount(null)
                .score(null)
                .build();
    }
}

