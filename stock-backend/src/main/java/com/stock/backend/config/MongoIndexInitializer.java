// AI_GENERATE_START --
package com.stock.backend.config;

import com.stock.trading.entity.AccountSnapshotEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * MongoDB 非文档集合索引与废弃索引初始化器。
 * 负责移除会阻止账户历史快照追加写入的旧唯一索引，并为 GridFS 快照文件建立业务唯一键。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
@RequiredArgsConstructor
public class MongoIndexInitializer implements ApplicationRunner {

    private static final String OBSOLETE_ACCOUNT_INDEX = "uk_account_snapshot_account";

    private final MongoTemplate mongoTemplate;

    /**
     * 初始化无法通过文档注解完整表达的 MongoDB 索引。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        removeObsoleteAccountSnapshotIndex();
        mongoTemplate.indexOps("fs.files").ensureIndex(new Index()
                .on("metadata.snapshotId", Sort.Direction.ASC)
                .on("metadata.fileName", Sort.Direction.ASC)
                .sparse()
                .unique()
                .named("uk_gridfs_snapshot_file"));
    }

    private void removeObsoleteAccountSnapshotIndex() {
        boolean exists = mongoTemplate.indexOps(AccountSnapshotEntity.class).getIndexInfo().stream()
                .anyMatch(index -> OBSOLETE_ACCOUNT_INDEX.equals(index.getName()));
        if (exists) {
            mongoTemplate.indexOps(AccountSnapshotEntity.class).dropIndex(OBSOLETE_ACCOUNT_INDEX);
        }
    }
}
// AI_GENERATE_END --
