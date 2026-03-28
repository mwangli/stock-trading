package com.stock.autoLogin.service;

import com.stock.autoLogin.dto.PopupDetectionResult;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 弹窗检测服务
 * 负责在触发验证码发送后检测页面弹窗状态，分析 DOM 结构变化，
 * 判断是否出现滑块验证弹窗、NECaptcha 组件等
 *
 * @author mwangli
 * @since 2026-03-28
 */
@Slf4j
@Service
public class PopupDetectionService {

    /**
     * 弹窗出现的默认等待时间（毫秒）
     */
    private static final int POPUP_WAIT_MS = 2000;

    /**
     * 检测弹窗状态
     * 等待弹窗出现后，分析 DOM 结构、iframe 变化、验证码组件特征
     *
     * @param driver WebDriver 实例
     * @return 弹窗检测结果
     */
    public PopupDetectionResult detectPopupAfterAction(WebDriver driver) {
        // 1. 等待弹窗出现
        sleepQuietly(POPUP_WAIT_MS);

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 2. 收集 body 直接子元素列表（查找新注入的弹窗层）
            @SuppressWarnings("unchecked")
            List<String> bodyChildren = (List<String>) js.executeScript(
                    "return Array.from(document.body.children).map(e => " +
                    "e.tagName + '#' + e.id + '.' + (e.className || '').substring(0,80) + " +
                    "' display=' + getComputedStyle(e).display + ' pos=' + getComputedStyle(e).position" +
                    ").slice(0, 30);"
            );

            // 3. 收集所有 iframe 信息
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> iframes = (List<Map<String, Object>>) js.executeScript(
                    "return Array.from(document.querySelectorAll('iframe')).map(f => " +
                    "({src: f.src?.substring(0,200), id: f.id, cls: f.className, " +
                    "w: f.offsetWidth, h: f.offsetHeight, display: getComputedStyle(f).display}));"
            );

            // 4. 获取窗口句柄数量
            int windowHandles = driver.getWindowHandles().size();

            // 5. 检测验证码组件关键词
            String bodyHtml = (String) js.executeScript(
                    "return document.body.innerHTML.substring(0, 8000);"
            );
            boolean hasYidunPopup = bodyHtml.contains("yidun_popup") || bodyHtml.contains("yidun_modal");
            boolean hasNECaptcha = bodyHtml.contains("NECaptcha") || bodyHtml.contains("necaptcha");
            boolean hasCaptchaDialog = bodyHtml.contains("安全验证") || bodyHtml.contains("拼图") || bodyHtml.contains("滑块");

            return PopupDetectionResult.builder()
                    .clicked(true)
                    .bodyChildren(bodyChildren)
                    .iframes(iframes)
                    .windowHandles(windowHandles)
                    .hasYidunPopup(hasYidunPopup)
                    .hasNECaptcha(hasNECaptcha)
                    .hasCaptchaDialog(hasCaptchaDialog)
                    .build();

        } catch (Exception e) {
            log.error("弹窗检测异常: {}", e.getMessage());
            return PopupDetectionResult.builder()
                    .clicked(true)
                    .detectError(e.getMessage())
                    .build();
        }
    }

    /**
     * 安全休眠（不抛出异常）
     *
     * @param millis 休眠毫秒数
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
