// AI_GENERATE_START----
package com.stock.trading.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户菜单偏好响应。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMenuPreferenceDto {

    /** 左侧菜单路由顺序。 */
    private List<String> menuOrder;
}
// AI_GENERATE_END----
