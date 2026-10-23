// AI_GENERATE_START------
package com.stock.trading.repository;

import com.stock.trading.entity.UserEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * 系统用户数据访问接口。
 *
 * @author mwangli
 * @since 2026-10-11
 */
public interface UserRepository extends MongoRepository<UserEntity, Long> {

    /** 按用户名查找用户。 */
    Optional<UserEntity> findByUsername(String username);
}
// AI_GENERATE_END------
