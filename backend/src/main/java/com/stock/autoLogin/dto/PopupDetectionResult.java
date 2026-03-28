package com.stock.autoLogin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 弹窗检测结果 DTO
 * 封装点击获取验证码后的弹窗检测信息，包括 DOM 结构、iframe 列表、验证码弹窗特征等
 *
 * @author mwangli
 * @since 2026-03-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopupDetectionResult {

    /**
     * 按钮是否已点击
     */
    private boolean clicked;

    /**
     * body 直接子元素列表（用于发现新注入的弹窗层）
     */
    private List<String> bodyChildren;

    /**
     * 页面中所有 iframe 信息
     */
    private List<Map<String, Object>> iframes;

    /**
     * 窗口句柄数量
     */
    private int windowHandles;

    /**
     * 是否检测到网易云盾弹窗
     */
    private boolean hasYidunPopup;

    /**
     * 是否检测到 NECaptcha 组件
     */
    private boolean hasNECaptcha;

    /**
     * 是否检测到验证码对话框（安全验证/拼图/滑块）
     */
    private boolean hasCaptchaDialog;

    /**
     * 检测过程中的错误信息
     */
    private String detectError;

    /**
     * 转换为 Map 用于 API 响应
     *
     * @return 包含所有检测信息的 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("clicked", clicked);
        if (bodyChildren != null) map.put("bodyChildren", bodyChildren);
        if (iframes != null) map.put("iframes", iframes);
        map.put("windowHandles", windowHandles);
        map.put("hasYidunPopup", hasYidunPopup);
        map.put("hasNECaptcha", hasNECaptcha);
        map.put("hasCaptchaDialog", hasCaptchaDialog);
        if (detectError != null) map.put("detectError", detectError);
        return map;
    }
}
