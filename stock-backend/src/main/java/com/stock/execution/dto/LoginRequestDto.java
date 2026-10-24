// AI_GENERATE_START-----
package com.stock.execution.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录请求参数。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
public class LoginRequestDto {

    /** 登录用户名。 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 登录密码。 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
// AI_GENERATE_END-----
