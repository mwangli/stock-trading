// AI_GENERATE_START------
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 情感模型运行状态 DTO。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentHealthDto {

    /** 服务状态。 */
    private String status;

    /** 服务名称。 */
    private String service;

    /** FinBERT 是否已加载。 */
    private boolean modelLoaded;

    /** 最近一次成功加载时间。 */
    private LocalDateTime lastLoadedTime;

    /** Mengzi 分类模型状态。 */
    private String mengziClassifierStatus;

    /** 最近一次加载或推理错误。 */
    private String lastError;
}
// AI_GENERATE_END------
