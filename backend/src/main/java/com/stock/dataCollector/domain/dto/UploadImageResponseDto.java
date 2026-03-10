package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片上传响应 DTO
 *
 * 对应 /api/ocr/uploadImage 接口返回结构。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadImageResponseDto {

    private boolean success;

    private UploadImageDataDto data;
}

