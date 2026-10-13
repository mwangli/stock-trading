// AI_GENERATE_START -
package com.stock.dataCollector.domain.enums;

/**
 * 交易日历休市类型。
 *
 * @author mwangli
 * @since 2026-10-13
 */
public enum CalendarClosureType {

    /** 正常开市，不存在休市原因。 */
    NONE,

    /** 周末休市。 */
    WEEKEND,

    /** 节假日休市。 */
    HOLIDAY,

    /** 尚未确认。 */
    UNKNOWN
}
// AI_GENERATE_END -
