package com.stock.tradingExecutor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前用户信息 DTO
 *
 * 对应 /api/login/currentUser 接口中的用户信息结构。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserDto {

    private String name;
    private String avatar;
    private String userid;
    private String email;
    private String signature;
    private String title;
    private String group;
    private Integer notifyCount;
    private Integer unreadCount;
    private String country;
    private String access;
}

