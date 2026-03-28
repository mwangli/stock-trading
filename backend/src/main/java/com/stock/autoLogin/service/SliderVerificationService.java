package com.stock.autoLogin.service;

import com.stock.autoLogin.dto.SliderVerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 滑块验证服务
 * 负责编排完整的滑块验证流程，包括检测滑块、提取图片、计算距离、执行拖动、验证结果，
 * 支持自动重试和滑块重新触发
 *
 * @author mwangli
 * @since 2026-03-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SliderVerificationService {

    private final CaptchaService captchaService;
    private final LoginPageHandler loginPageHandler;
    private final BrowserSessionManager browserSessionManager;

    @Value("${slider.max-attempts:3}")
    private int maxAttempts;

    @Value("${slider.retry-delay:1500}")
    private int retryDelay;

    /**
     * 执行滑块验证（带自动重试）
     * 完整流程：检测滑块 → 提取图片 → 计算距离 → 执行拖动 → 验证结果
     * 如果验证失败，自动重试最多 maxAttempts 次
     *
     * @param driver WebDriver 实例
     * @return 滑块验证结果
     */
    public SliderVerificationResult solveWithRetry(WebDriver driver) {
        SliderVerificationResult lastResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            log.info("滑块验证第 {}/{} 次尝试", attempt, maxAttempts);

            // 1. 执行单次验证尝试
            lastResult = attemptOnce(driver, attempt);

            // 2. 成功则直接返回
            if (lastResult.isSuccess()) {
                log.info("滑块验证成功！第 {} 次尝试", attempt);
                return lastResult;
            }

            // 3. 非最后一次失败则等待重试
            if (attempt < maxAttempts) {
                log.warn("滑块验证失败，准备第 {} 次重试...", attempt + 1);
                sleepQuietly(retryDelay);
            }
        }

        log.error("滑块验证失败：已达最大尝试次数 {}", maxAttempts);
        return lastResult;
    }

    /**
     * 执行单次滑块验证尝试
     * 包含滑块检测、图片提取、距离计算、拖动执行、结果验证的完整流程
     *
     * @param driver  WebDriver 实例
     * @param attempt 当前尝试次数
     * @return 本次尝试的验证结果
     */
    private SliderVerificationResult attemptOnce(WebDriver driver, int attempt) {
        try {
            // 1. 切换到滑块所在 frame（如果不在，尝试重新触发）
            if (!captchaService.switchToSliderFrame(driver)) {
                log.info("滑块未弹出，尝试重新触发");
                if (!retriggerSlider(driver)) {
                    return SliderVerificationResult.failure("滑块弹窗未弹出", attempt);
                }
            }

            // 2. 提取背景图和拼图块 URL
            CaptchaService.ImageUrls urls = captchaService.extractYidunImageUrls(driver);
            if (urls.getBgUrl() == null || urls.getSliderUrl() == null) {
                return SliderVerificationResult.failure("无法提取滑块图片 URL", attempt);
            }

            // 3. 下载图片并计算滑动距离（使用动态渲染宽度）
            byte[] bgImage = captchaService.downloadImage(urls.getBgUrl());
            byte[] sliderImage = captchaService.downloadImage(urls.getSliderUrl());
            int renderedWidth = captchaService.getRenderedSliderWidth(driver);
            int distance = captchaService.calculateSliderDistance(bgImage, sliderImage, renderedWidth);
            log.info("滑块距离: {}px (渲染宽度={}px, 第 {}/{} 次)", distance, renderedWidth, attempt, maxAttempts);

            if (distance <= 0) {
                return SliderVerificationResult.failure("距离计算失败", attempt);
            }

            // 4. 执行滑块拖动
            boolean dragSuccess = captchaService.executeSliderDrag(driver, distance);
            if (!dragSuccess) {
                return SliderVerificationResult.failure("拖动失败", attempt);
            }

            // 5. 等待验证结果
            boolean verified = captchaService.waitForVerificationResult(driver, 1);
            if (verified) {
                return SliderVerificationResult.success(distance, attempt);
            }

            return SliderVerificationResult.failure("验证未通过", attempt);

        } catch (Exception e) {
            log.error("滑块验证异常 (第 {}/{} 次): {}", attempt, maxAttempts, e.getMessage());
            return SliderVerificationResult.failure(e.getMessage(), attempt);
        }
    }

    /**
     * 重新触发滑块弹窗
     * 切换回登录表单 iframe，重新点击获取验证码按钮以触发滑块
     *
     * @param driver WebDriver 实例
     * @return 是否成功触发滑块
     */
    private boolean retriggerSlider(WebDriver driver) {
        try {
            browserSessionManager.ensureLoginFrame();
            loginPageHandler.clickSendCodeButton();
            sleepQuietly(3000);
            return captchaService.switchToSliderFrame(driver);
        } catch (Exception e) {
            log.error("重新触发滑块失败: {}", e.getMessage());
            return false;
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
