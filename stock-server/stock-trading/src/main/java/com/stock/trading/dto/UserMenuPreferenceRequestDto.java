// AI_GENERATE_START------
package com.stock.trading.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户菜单偏好保存请求。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
public class UserMenuPreferenceRequestDto {

    /** 左侧菜单路由顺序。 */
    @NotNull
    @Size(max = 64)
    private List<@NotBlank @Size(max = 128) String> menuOrder;
}
// AI_GENERATE_END------
