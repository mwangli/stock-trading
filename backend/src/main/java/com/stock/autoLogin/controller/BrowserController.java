package com.stock.autoLogin.controller;

import com.stock.autoLogin.service.BrowserSessionManager;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 浏览器通用操作控制器
 * 负责浏览器启动、关闭、导航、iframe 切换等基础操作
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Slf4j
@RestController
@RequestMapping("/api/browser")
@RequiredArgsConstructor
public class BrowserController {

    private final BrowserSessionManager browserSessionManager;

    /**
     * 启动浏览器
     *
     * @return 操作结果
     */
    @PostMapping("/start")
    public ResponseDTO<Void> startBrowser() {
        log.info("启动浏览器");
        browserSessionManager.startBrowser();
        return ResponseDTO.success(null, "浏览器启动成功");
    }

    /**
     * 访问登录页面（等待页面加载完成后返回）
     *
     * @return 操作结果
     */
    @PostMapping("/navigate/login")
    public ResponseDTO<Void> navigateToLogin() {
        log.info("访问登录页面");
        browserSessionManager.startBrowser();
        WebDriver driver = browserSessionManager.getDriver();
        driver.get("https://weixin.citicsinfo.com/tztweb/deal/index.html#!/account/login.html");
        try {
            new org.openqa.selenium.support.ui.WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> !d.findElements(By.xpath(
                            "//input[contains(@placeholder, '手机号')] | //input[@type='password']"
                    )).isEmpty());
            log.info("登录页面加载完成");
        } catch (Exception e) {
            log.warn("等待页面加载超时，继续执行: {}", e.getMessage());
        }
        return ResponseDTO.success(null, "已访问登录页面");
    }

    /**
     * 刷新当前页面
     *
     * @return 操作结果
     */
    @PostMapping("/refresh")
    public ResponseDTO<Void> refreshPage() {
        log.info("刷新当前页面");
        WebDriver driver = browserSessionManager.getDriver();
        // 1. 使用 JS 硬刷新代替 navigate().refresh()，解决 SPA hash 路由页面刷新后白屏问题
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("location.reload(true)");
        // 2. 等待页面关键元素加载
        try {
            new org.openqa.selenium.support.ui.WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> !d.findElements(By.xpath(
                            "//input[contains(@placeholder, '手机号')] | //input[@type='password']"
                    )).isEmpty());
            log.info("页面刷新完成，内容已加载");
        } catch (Exception e) {
            log.warn("刷新后等待页面加载超时: {}", e.getMessage());
        }
        return ResponseDTO.success(null, "页面刷新完成");
    }

    /**
     * 切换到登录 iframe
     *
     * @return 是否切换成功
     */
    @PostMapping("/frame/switch")
    public ResponseDTO<Boolean> switchToLoginFrame() {
        log.info("切换到登录 iframe");
        boolean success = browserSessionManager.ensureLoginFrame();
        return ResponseDTO.success(success, success ? "已切换到登录 iframe" : "未找到登录 iframe");
    }

    /**
     * 查询浏览器状态
     *
     * @return 浏览器运行状态信息
     */
    @GetMapping("/status")
    public ResponseDTO<Map<String, Object>> browserStatus() {
        log.info("查询浏览器状态");
        Map<String, Object> status = new HashMap<>();
        boolean alive = browserSessionManager.isBrowserAlive();
        status.put("alive", alive);
        if (alive) {
            WebDriver driver = browserSessionManager.getDriver();
            status.put("currentUrl", driver.getCurrentUrl());
            status.put("title", driver.getTitle());
        }
        return ResponseDTO.success(status);
    }

    /**
     * 关闭浏览器
     *
     * @return 操作结果
     */
    @PostMapping("/quit")
    public ResponseDTO<Void> quitBrowser() {
        log.info("关闭浏览器");
        browserSessionManager.quitBrowser();
        return ResponseDTO.success(null, "浏览器已关闭");
    }
}
