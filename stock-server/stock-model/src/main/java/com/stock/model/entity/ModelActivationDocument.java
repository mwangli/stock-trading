// AI_GENERATE_START-----
package com.stock.model.entity;

import lombok.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * 模型激活指针实体。
 * 将可变的生产指针与不可变模型版本分离，并记录上一版本用于快速回滚。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Entity
@Table(name = "model_activation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_model_activation_name", columnNames = "model_name")
})
public class ModelActivationDocument {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    private String modelName;

    private String activeModelVersionId;

    private String previousModelVersionId;

    private LocalDateTime activatedAt;
}
// AI_GENERATE_END-----
