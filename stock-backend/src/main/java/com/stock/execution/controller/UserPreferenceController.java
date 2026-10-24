// AI_GENERATE_START-------
package com.stock.execution.controller;

import com.stock.platform.dto.ResponseDTO;
import com.stock.execution.dto.UserMenuPreferenceDto;
import com.stock.execution.dto.UserMenuPreferenceRequestDto;
import com.stock.execution.service.UserPreferenceService;
import com.stock.execution.interceptor.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户界面偏好接口。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/userPreferences")
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    /**
     * 查询当前用户的左侧菜单顺序。
     *
     * @param request HTTP 请求
     * @return 菜单顺序
     */
    @GetMapping("/menuOrder")
    public ResponseDTO<UserMenuPreferenceDto> getMenuOrder(HttpServletRequest request) {
        Long userId = currentUserId(request);
        log.info("查询用户菜单顺序，userId={}", userId);
        return ResponseDTO.success(userPreferenceService.getMenuPreference(userId));
    }

    /**
     * 保存当前用户的左侧菜单顺序。
     *
     * @param request HTTP 请求
     * @param preference 菜单顺序请求
     * @return 保存后的菜单顺序
     */
    @PutMapping("/menuOrder")
    public ResponseDTO<UserMenuPreferenceDto> saveMenuOrder(
            HttpServletRequest request,
            @Valid @RequestBody UserMenuPreferenceRequestDto preference) {
        Long userId = currentUserId(request);
        log.info("保存用户菜单顺序，userId={}, menuCount={}", userId, preference.getMenuOrder().size());
        return ResponseDTO.success(userPreferenceService.saveMenuPreference(userId, preference));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getSession(false)
                .getAttribute(AuthInterceptor.AUTH_USER_ID_SESSION_KEY);
        if (userId instanceof Long value) {
            return value;
        }
        throw new IllegalStateException("当前登录用户不存在");
    }
}
// AI_GENERATE_END-------
