package com.stock.autoLogin.service;

import com.stock.autoLogin.enums.PageType;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 页面类型检测器
 * 统一的页面类型检测逻辑，基于 DOM 元素可见性判断当前是手机验证页还是登录页
 *
 * @author mwangli
 * @since 2026-03-28
 */
@Slf4j
@Service
public class PageTypeDetector {

    /**
     * 检测当前页面类型
     *
     * @param driver WebDriver 实例
     * @return 页面类型枚举
     */
    public PageType detect(WebDriver driver) {
        try {
            // 1. 检测手机验证页特征：手机号输入框可见 + 获取验证码按钮可见
            boolean hasVisiblePhoneInput = hasVisibleElement(driver,
                    By.xpath("//input[contains(@placeholder, '手机号')]"));
            boolean hasVisibleSendCode = hasVisibleElement(driver,
                    By.xpath("//*[contains(text(), '获取验证码')]"));

            // 2. 检测登录页特征：密码输入框可见 或 资金账号输入框可见
            boolean hasVisiblePassword = hasVisibleElement(driver,
                    By.xpath("//input[@type='password']"));
            boolean hasVisibleAccount = hasVisibleElement(driver,
                    By.xpath("//input[contains(@placeholder, '资金账号') or contains(@placeholder, '证券账号')]"));

            // 3. 综合判断
            if (hasVisiblePhoneInput && hasVisibleSendCode && !hasVisiblePassword) {
                log.info("检测到页面类型：PHONE_VERIFY - phone={}, sendCode={}, password={}",
                        hasVisiblePhoneInput, hasVisibleSendCode, hasVisiblePassword);
                return PageType.PHONE_VERIFY;
            }

            if (hasVisiblePassword || hasVisibleAccount) {
                log.info("检测到页面类型：LOGIN - password={}, account={}",
                        hasVisiblePassword, hasVisibleAccount);
                return PageType.LOGIN;
            }

            // 4. 备选判断：密码输入框存在（不论可见性）
            boolean hasPasswordElement = !driver.findElements(
                    By.xpath("//input[@type='password']")).isEmpty();
            if (hasPasswordElement) {
                log.info("备选判断：密码输入框存在，判定为 LOGIN");
                return PageType.LOGIN;
            }

            log.warn("无法确定页面类型");
            return PageType.UNKNOWN;

        } catch (Exception e) {
            log.error("页面类型检测失败: {}", e.getMessage());
            return PageType.UNKNOWN;
        }
    }

    /**
     * 判断当前页面是否包含登录或验证表单元素（用于 iframe 定位）
     *
     * @param driver WebDriver 实例
     * @return 是否包含表单元素
     */
    public boolean hasFormElements(WebDriver driver) {
        // 标准登录页：有 password 输入框
        if (!driver.findElements(By.xpath("//input[@type='password']")).isEmpty()) {
            return true;
        }
        // 手机验证页：有"获取验证码"按钮或"手机号"输入框
        return !driver.findElements(By.xpath(
                "//*[contains(text(), '获取验证码')] | " +
                "//input[contains(@placeholder, '手机号')]"
        )).isEmpty();
    }

    /**
     * 检查指定定位器是否有可见元素
     *
     * @param driver  WebDriver 实例
     * @param locator 元素定位器
     * @return 是否有可见元素
     */
    private boolean hasVisibleElement(WebDriver driver, By locator) {
        List<WebElement> elements = driver.findElements(locator);
        return elements.stream().anyMatch(el -> {
            try {
                return el.isDisplayed();
            } catch (Exception e) {
                return false;
            }
        });
    }
}
