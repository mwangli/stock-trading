// AI_GENERATE_START -
package com.stock.modelService.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 模型激活指针文档。
 * 将可变的生产指针与不可变模型版本分离，并记录上一版本用于快速回滚。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Document(collection = "model_activations")
public class ModelActivationDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String modelName;

    private String activeModelVersionId;

    private String previousModelVersionId;

    private LocalDateTime activatedAt;
}
// AI_GENERATE_END -
