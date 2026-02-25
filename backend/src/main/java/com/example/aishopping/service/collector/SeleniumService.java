package com.example.aishopping.service.collector;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Selenium 浏览器服务
 * 使用 Chrome 浏览器模拟真实用户行为，避免被反爬虫检测
 */
@Service
@Slf4j
public class SeleniumService {

    private ChromeDriver driver;
    private final int TIMEOUT_SECONDS = 30;

    /**
     * 初始化 Chrome 浏览器
     */
    public void initDriver() {
        if (driver == null || !driver.getCurrentUrl().isEmpty()) {
            try {
                // 设置 ChromeDriver 路径
                System.setProperty("webdriver.chrome.driver", "D:/chromedriver/chromedriver-win64/chromedriver.exe");

                ChromeOptions options = new ChromeOptions();
                options.addArguments("--headless=new"); // 无头模式
                options.addArguments("--no-sandbox");
                options.addArguments("--disable-dev-shm-usage");
                options.addArguments("--disable-gpu");
                options.addArguments("--window-size=1920,1080");
                options.addArguments("--disable-blink-features=AutomationControlled");
                options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                options.addArguments("--remote-allow-origins=*");
                options.addArguments("--disable-extensions");
                options.addArguments("--disable-popup-blocking");
                options.addArguments("--ignore-certificate-errors");
                options.addArguments("--allow-insecure-localhost");
                options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation"));
                options.setExperimentalOption("useAutomationExtension", false);

                // 使用 Selenium Manager 自动管理 ChromeDriver
                driver = new ChromeDriver(options);

                // 设置隐式等待
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
                driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
                driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

                log.info("[SELENIUM] ChromeDriver 初始化成功");
            } catch (Exception e) {
                log.error("[SELENIUM] ChromeDriver 初始化失败: {}", e.getMessage(), e);
                throw new RuntimeException("ChromeDriver 初始化失败", e);
            }
        }
    }

    /**
     * 获取页面内容
     *
     * @param url 页面 URL
     * @return 页面 HTML 内容
     */
    public String getPageContent(String url) {
        log.info("[SELENIUM] 开始访问 URL: {}", url);

        try {
            // 确保驱动已初始化
            if (driver == null || driver.getCurrentUrl().isEmpty()) {
                initDriver();
            }

            // 访问页面
            driver.get(url);

            // 等待页面加载完成
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SECONDS));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // 等待 React/Next.js 渲染完成
            waitForReactToLoad();

            // 执行 JavaScript 滚动，模拟真实用户行为
            scrollToBottom();

            // 获取页面内容
            String content = driver.getPageSource();

            log.info("[SELENIUM] 成功获取页面内容，长度: {} 字符", content.length());
            return content;

        } catch (TimeoutException e) {
            log.error("[SELENIUM] 页面加载超时: {}", url);
            throw new RuntimeException("页面加载超时: " + url, e);
        } catch (NoSuchElementException e) {
            log.error("[SELENIUM] 页面元素未找到: {}", url);
            throw new RuntimeException("页面元素未找到: " + url, e);
        } catch (Exception e) {
            log.error("[SELENIUM] 获取页面内容失败: {}", url, e);
            throw new RuntimeException("获取页面内容失败: " + url, e);
        }
    }

    /**
     * 等待 React/Next.js 渲染完成
     */
    private void waitForReactToLoad() {
        try {
            // 等待网络请求完成
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // 等待页面加载完成
            sleep(3000);
            
            // 检查是否有 loading 状态
            int waitCount = 0;
            while (waitCount < 10) {
                Boolean isLoading = (Boolean) js.executeScript(
                    "return document.readyState === 'complete' && " +
                    "(typeof window.__NEXT_DATA__ !== 'undefined' || " +
                    "document.querySelectorAll('[data-testid]').length > 0)"
                );
                if (Boolean.TRUE.equals(isLoading)) {
                    break;
                }
                sleep(1000);
                waitCount++;
            }
            
            log.info("[SELENIUM] React 渲染等待完成");
        } catch (Exception e) {
            log.warn("[SELENIUM] 等待渲染时出错: {}", e.getMessage());
        }
    }

    /**
     * 滚动到页面底部
     */
    private void scrollToBottom() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(2000); // 等待滚动动画
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[SELENIUM] 滚动被中断");
        }
    }

    /**
     * 等待指定时间
     *
     * @param milliseconds 毫秒数
     */
    public void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 关闭浏览器
     */
    public void closeDriver() {
        if (driver != null) {
            try {
                driver.quit();
                driver = null;
                log.info("[SELENIUM] ChromeDriver 已关闭");
            } catch (Exception e) {
                log.error("[SELENIUM] 关闭 ChromeDriver 失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取当前页面 URL
     */
    public String getCurrentUrl() {
        return driver != null ? driver.getCurrentUrl() : "";
    }

    /**
     * 检查是否需要加载驱动
     */
    public boolean isDriverReady() {
        return driver != null && !driver.getCurrentUrl().isEmpty();
    }
}
