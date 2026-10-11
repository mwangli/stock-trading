// AI_GENERATE_START
package com.stock.user.service;

import com.stock.user.domain.dto.AuthUserDto;
import com.stock.user.domain.entity.UserEntity;
import com.stock.user.persistence.UserRepository;
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

    /** 校验用户名和密码。 */
    public UserEntity authenticate(String username, String password) {
        return userRepository.findByUsername(username.trim())
                .filter(UserEntity::isEnabled)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .orElse(null);
    }

    /** 将用户实体转换为对外安全的用户信息。 */
    public AuthUserDto toDto(UserEntity user) {
        return AuthUserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .build();
    }

    /** 初始化默认用户；已存在用户不会覆盖密码或其他信息。 */
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
// AI_GENERATE_END
