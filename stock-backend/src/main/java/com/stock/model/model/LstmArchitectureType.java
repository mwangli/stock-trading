// AI_GENERATE_START -
package com.stock.model.model;

/**
 * LSTM 候选模型架构类型。
 * 用于训练配置、制品元数据和运行时模型重建。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public enum LstmArchitectureType {

    /** 横截面排序 LSTM。 */
    RANKING_LSTM,

    /** 特征注意力和时间注意力增强的横截面排序 LSTM。 */
    DUAL_ATTENTION_RANKING_LSTM,

    /** 历史三输出多任务 LSTM，仅用于兼容旧制品。 */
    LEGACY_MULTITASK_LSTM
}
// AI_GENERATE_END -
