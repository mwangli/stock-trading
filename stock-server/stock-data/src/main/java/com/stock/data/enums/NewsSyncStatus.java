// AI_GENERATE_START-----
package com.stock.data.enums;

/**
 * 股票新闻同步状态。
 *
 * @author mwangli
 * @since 2026-10-13
 */
public enum NewsSyncStatus {

    /** 尚未执行过新闻同步。 */
    NEVER_SYNCED,

    /** 新闻同步正在执行。 */
    RUNNING,

    /** 新闻和公告均同步成功且存在新增数据。 */
    SUCCESS_WITH_CHANGE,

    /** 新闻和公告均同步成功但没有新增数据。 */
    SUCCESS_NO_CHANGE,

    /** 新闻或公告仅有一个来源同步成功。 */
    PARTIAL,

    /** 新闻和公告两个来源均同步失败。 */
    FAILED
}
// AI_GENERATE_END-----
