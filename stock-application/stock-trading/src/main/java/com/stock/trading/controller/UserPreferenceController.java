// AI_GENERATE_START-----
package com.stock.trading.controller;

import com.stock.base.api.ResponseDTO;
import com.stock.trading.dto.AuthUserDto;
import com.stock.trading.dto.UserMenuPreferenceDto;
import com.stock.trading.dto.UserMenuPreferenceRequestDto;
import com.stock.trading.service.UserPreferenceService;
import com.stock.trading.web.AuthInterceptor;
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
        AuthUserDto user = currentUser(request);
        log.info("查询用户菜单顺序，userId={}", user.getId());
        return ResponseDTO.success(userPreferenceService.getMenuPreference(user.getId()));
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
        AuthUserDto user = currentUser(request);
        log.info("保存用户菜单顺序，userId={}, menuCount={}", user.getId(), preference.getMenuOrder().size());
        return ResponseDTO.success(userPreferenceService.saveMenuPreference(user.getId(), preference));
    }

    private AuthUserDto currentUser(HttpServletRequest request) {
        Object user = request.getSession(false).getAttribute(AuthInterceptor.AUTH_USER_SESSION_KEY);
        if (user instanceof AuthUserDto authUser) {
            return authUser;
        }
        throw new IllegalStateException("当前登录用户不存在");
    }
}
// AI_GENERATE_END-----
