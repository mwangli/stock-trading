// AI_GENERATE_START-----
package com.stock.trading.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import org.springframework.data.annotation.Id;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统登录用户实体，保存系统用户的认证信息和展示信息。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Document(collection = "sys_user")
public class UserEntity {

    /** 数据库主键。 */
    @Id
    private Long id;

    /** 登录用户名。 */
    @Indexed(unique = true, name = "uk_system_user_name")
    private String username;

    /** BCrypt 密码摘要。 */
    private String passwordHash;

    /** 用户显示名称。 */
    private String displayName;

    /** 是否允许登录。 */
    private boolean enabled;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 初始化创建时间。 */
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    /** 刷新更新时间。 */
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END-----
