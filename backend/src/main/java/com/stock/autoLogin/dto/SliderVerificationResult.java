package com.stock.autoLogin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 滑块验证结果 DTO
 * 封装滑块验证流程的执行结果，包括成功状态、距离、尝试次数等信息
 *
 * @author mwangli
 * @since 2026-03-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SliderVerificationResult {

    /**
     * 是否验证成功
     */
    private boolean success;

    /**
     * 滑块滑动距离（像素）
     */
    private Integer distance;

    /**
     * 当前尝试次数
     */
    private Integer attempt;

    /**
     * 错误信息（失败时填写）
     */
    private String error;

    /**
     * 背景图 URL 获取状态
     */
    private String bgUrlStatus;

    /**
     * 拼图块 URL 获取状态
     */
    private String sliderUrlStatus;

    /**
     * 拖动是否成功
     */
    private Boolean dragSuccess;

    /**
     * 验证是否通过
     */
    private Boolean verified;

    /**
     * 创建成功结果
     *
     * @param distance 滑动距离
     * @param attempt  尝试次数
     * @return 成功的验证结果
     */
    public static SliderVerificationResult success(int distance, int attempt) {
        return SliderVerificationResult.builder()
                .success(true)
                .distance(distance)
                .attempt(attempt)
                .dragSuccess(true)
                .verified(true)
                .build();
    }

    /**
     * 创建失败结果
     *
     * @param error   错误信息
     * @param attempt 尝试次数
     * @return 失败的验证结果
     */
    public static SliderVerificationResult failure(String error, int attempt) {
        return SliderVerificationResult.builder()
                .success(false)
                .error(error)
                .attempt(attempt)
                .build();
    }

    /**
     * 转换为 Map 用于 API 响应
     *
     * @return 包含所有非空字段的 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        if (distance != null) map.put("distance", distance);
        if (attempt != null) map.put("attempt", attempt);
        if (error != null) map.put("error", error);
        if (bgUrlStatus != null) map.put("bgUrl", bgUrlStatus);
        if (sliderUrlStatus != null) map.put("sliderUrl", sliderUrlStatus);
        if (dragSuccess != null) map.put("dragSuccess", dragSuccess);
        if (verified != null) map.put("verified", verified);
        return map;
    }
}
