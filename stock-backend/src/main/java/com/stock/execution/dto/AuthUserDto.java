// AI_GENERATE_START-----
package com.stock.execution.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 当前登录用户信息。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Value
@Builder
public class AuthUserDto {

    /** 用户主键。 */
    Long id;

    /** 登录用户名。 */
    String username;

    /** 用户显示名称。 */
    String displayName;
}
// AI_GENERATE_END-----
