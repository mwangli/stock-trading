// AI_GENERATE_START------
package com.stock.trading.repository;

import com.stock.trading.entity.UserPreferenceEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 用户界面偏好数据访问接口。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public interface UserPreferenceRepository extends MongoRepository<UserPreferenceEntity, Long> {
}
// AI_GENERATE_END------
