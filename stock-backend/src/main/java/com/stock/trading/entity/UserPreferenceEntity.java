// AI_GENERATE_START-----
package com.stock.trading.entity;

import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Id;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户界面偏好实体，保存需要跨设备恢复的用户级界面配置。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Document(collection = "sys_user_preference")
public class UserPreferenceEntity {

    /** 关联的系统用户主键。 */
    @Id
    private Long userId;

    /** 左侧菜单顺序，使用 JSON 数组文本保存。 */
    private String menuOrder;

    /** 偏好最后更新时间。 */
    private LocalDateTime updatedAt;

    /** 初始化偏好创建时间。 */
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    /** 刷新偏好更新时间。 */
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END-----
