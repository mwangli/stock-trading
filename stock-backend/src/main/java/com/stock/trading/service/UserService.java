// AI_GENERATE_START------
package com.stock.trading.service;

import com.stock.trading.dto.AuthUserDto;
import com.stock.trading.entity.UserEntity;
import com.stock.trading.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户认证服务，负责用户校验和默认用户初始化。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 校验用户名、密码和用户启用状态。
     *
     * @param username 登录用户名
     * @param password 登录密码明文
     * @return 认证成功的用户；认证失败时返回 null
     */
    public UserEntity authenticate(String username, String password) {
        return userRepository.findByUsername(username.trim())
                .filter(UserEntity::isEnabled)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .orElse(null);
    }

    /**
     * 将用户实体转换为不包含密码信息的登录用户 DTO。
     *
     * @param user 用户实体
     * @return 登录用户 DTO
     */
    public AuthUserDto toDto(UserEntity user) {
        return AuthUserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .build();
    }

    /**
     * 幂等初始化默认用户，已存在用户不会覆盖密码或其他信息。
     *
     * @param username 登录用户名
     * @param displayName 用户显示名称
     */
    public void initializeDefaultUser(String username, String displayName) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("123456"));
        user.setDisplayName(displayName);
        user.setEnabled(true);
        userRepository.save(user);
    }
}
// AI_GENERATE_END------
