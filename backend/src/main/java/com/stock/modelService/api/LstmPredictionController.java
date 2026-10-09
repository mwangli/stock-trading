// AI_GENERATE_START --
package com.stock.modelService.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.modelService.domain.dto.LstmPredictionResultDto;
import com.stock.modelService.service.LstmInferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LSTM 预测接口。
 * 模型训练仅允许由收盘后离线任务触发，不开放在线训练和模型管理接口。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Slf4j
@RestController
@RequestMapping("/api/lstm")
@RequiredArgsConstructor
public class LstmPredictionController {

    private final LstmInferenceService lstmInferenceService;

    /**
     * 使用当前生效模型预测下一交易日价格。
     *
     * @param stockCode 股票代码
     * @return LSTM 预测结果
     */
    @GetMapping("/predict")
    public ResponseDTO<LstmPredictionResultDto> predictNext(@RequestParam String stockCode) {
        log.info("执行 LSTM 预测: stockCode={}", stockCode);
        try {
            return ResponseDTO.success(lstmInferenceService.predictNext(stockCode));
        } catch (RuntimeException exception) {
            log.error("LSTM 预测失败: stockCode={}", stockCode, exception);
            return ResponseDTO.error("预测失败：" + exception.getMessage());
        }
    }
}
// AI_GENERATE_END --
