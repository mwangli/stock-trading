// AI_GENERATE_START--------
package com.stock.execution.repository;

import com.stock.execution.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 系统用户数据访问接口。
 *
 * @author mwangli
 * @since 2026-10-11
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /** 按用户名查找用户。 */
    Optional<UserEntity> findByUsername(String username);
}
// AI_GENERATE_END--------
