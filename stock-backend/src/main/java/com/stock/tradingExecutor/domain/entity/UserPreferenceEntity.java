// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户界面偏好实体，保存需要跨设备恢复的用户级界面配置。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Entity
@Table(name = "sys_user_preference")
public class UserPreferenceEntity {

    /** 关联的系统用户主键。 */
    @Id
    @Column(name = "user_id")
    private Long userId;

    /** 左侧菜单顺序，使用 JSON 数组文本保存。 */
    @Column(name = "menu_order", nullable = false, columnDefinition = "TEXT")
    private String menuOrder;

    /** 偏好最后更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 初始化偏好创建时间。 */
    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    /** 刷新偏好更新时间。 */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END --
