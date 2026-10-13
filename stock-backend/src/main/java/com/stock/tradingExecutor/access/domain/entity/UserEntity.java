// AI_GENERATE_START -
package com.stock.tradingExecutor.access.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统登录用户实体，保存系统用户的认证信息和展示信息。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Entity
@Table(name = "sys_user")
public class UserEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户名。 */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** BCrypt 密码摘要。 */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** 用户显示名称。 */
    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    /** 是否允许登录。 */
    @Column(nullable = false)
    private boolean enabled;

    /** 创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 初始化创建时间。 */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    /** 刷新更新时间。 */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END -
