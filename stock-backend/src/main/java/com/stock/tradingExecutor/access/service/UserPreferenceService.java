// AI_GENERATE_START -
package com.stock.tradingExecutor.access.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.tradingExecutor.access.domain.dto.UserMenuPreferenceDto;
import com.stock.tradingExecutor.access.domain.dto.UserMenuPreferenceRequestDto;
import com.stock.tradingExecutor.access.domain.entity.UserPreferenceEntity;
import com.stock.tradingExecutor.access.persistence.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户界面偏好服务，负责菜单顺序等用户级配置的持久化。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private static final TypeReference<List<String>> MENU_ORDER_TYPE = new TypeReference<>() {
    };

    private final UserPreferenceRepository userPreferenceRepository;
    private final ObjectMapper objectMapper;

    /**
     * 查询用户保存的菜单顺序。
     *
     * @param userId 用户主键
     * @return 用户菜单偏好；尚未保存时返回空顺序
     */
    @Transactional(readOnly = true)
    public UserMenuPreferenceDto getMenuPreference(Long userId) {
        return userPreferenceRepository.findById(userId)
                .map(this::toDto)
                .orElseGet(() -> emptyPreference());
    }

    /**
     * 保存用户的菜单顺序。
     *
     * @param userId 用户主键
     * @param request 菜单顺序请求
     * @return 保存后的菜单偏好
     */
    @Transactional
    public UserMenuPreferenceDto saveMenuPreference(Long userId, UserMenuPreferenceRequestDto request) {
        UserPreferenceEntity preference = userPreferenceRepository.findById(userId)
                .orElseGet(UserPreferenceEntity::new);
        preference.setUserId(userId);
        preference.setMenuOrder(writeMenuOrder(request.getMenuOrder()));
        return toDto(userPreferenceRepository.save(preference));
    }

    private UserMenuPreferenceDto toDto(UserPreferenceEntity preference) {
        return UserMenuPreferenceDto.builder()
                .menuOrder(readMenuOrder(preference.getMenuOrder()))
                .build();
    }

    private UserMenuPreferenceDto emptyPreference() {
        return UserMenuPreferenceDto.builder().menuOrder(List.of()).build();
    }

    private String writeMenuOrder(List<String> menuOrder) {
        try {
            return objectMapper.writeValueAsString(menuOrder.stream().distinct().toList());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("菜单顺序保存失败", exception);
        }
    }

    private List<String> readMenuOrder(String menuOrder) {
        try {
            return objectMapper.readValue(menuOrder, MENU_ORDER_TYPE);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
// AI_GENERATE_END -
