package com.stock.autoLogin.controller;

import com.stock.autoLogin.enums.PageType;
import com.stock.autoLogin.service.BrowserSessionManager;
import com.stock.autoLogin.service.DiagnosticService;
import com.stock.autoLogin.service.PageTypeDetector;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断接口控制器
 * 提供页面类型检测、截图、DOM 诊断、表单状态查询、滑块轮询、指纹检查等调试接口
 * 所有业务逻辑委托给 DiagnosticService 和 PageTypeDetector 处理
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Slf4j
@RestController
@RequestMapping("/api/browser/debug")
@RequiredArgsConstructor
public class DiagnosticController {

    private final BrowserSessionManager browserSessionManager;
    private final DiagnosticService diagnosticService;
    private final PageTypeDetector pageTypeDetector;

    /**
     * 检测页面类型（基于 DOM 内容，非 URL）
     *
     * @return 页面类型及描述
     */
    @GetMapping("/page-type")
    public ResponseDTO<Map<String, Object>> detectPageType() {
        log.info("检测页面类型");
        WebDriver driver = browserSessionManager.getDriver();
        PageType pageType = pageTypeDetector.detect(driver);

        Map<String, Object> result = new HashMap<>();
        result.put("pageType", pageType.name());
        result.put("description", pageType.getDescription());
        result.put("currentUrl", driver.getCurrentUrl());
        return ResponseDTO.success(result);
    }

    /**
     * 全页面截图
     *
     * @return 截图文件路径
     */
    @GetMapping("/screenshot")
    public ResponseDTO<String> screenshot() {
        log.info("截取页面截图");
        WebDriver driver = browserSessionManager.getDriver();
        String filePath = diagnosticService.takeScreenshot(driver);
        return ResponseDTO.success(filePath, "截图已保存");
    }

    /**
     * DOM 诊断 — 在顶层和所有 iframe 中搜索 .yidun 元素
     *
     * @return 每个 frame 的搜索结果
     */
    @GetMapping("/dom-inspect")
    public ResponseDTO<List<Map<String, Object>>> domInspect() {
        log.info("DOM 诊断 — 搜索 .yidun 元素");
        WebDriver driver = browserSessionManager.getDriver();
        List<Map<String, Object>> results = diagnosticService.inspectYidunElements(driver);
        return ResponseDTO.success(results);
    }

    /**
     * 表单状态 — 返回当前 frame 中所有 input/button 的状态
     *
     * @return 表单元素状态
     */
    @GetMapping("/form-state")
    public ResponseDTO<Map<String, Object>> formState() {
        log.info("查询表单状态");
        WebDriver driver = browserSessionManager.getDriver();
        Map<String, Object> state = diagnosticService.inspectFormState(driver);
        return ResponseDTO.success(state);
    }

    /**
     * 滑块轮询 — 持续 N 秒在所有 frame 中轮询 .yidun 元素
     *
     * @param seconds 轮询秒数，默认 10 秒
     * @return 每秒检测结果
     */
    @GetMapping("/slider-poll")
    public ResponseDTO<List<Map<String, Object>>> sliderPoll(
            @RequestParam(defaultValue = "10") int seconds) {
        log.info("滑块轮询 {} 秒", seconds);
        WebDriver driver = browserSessionManager.getDriver();
        List<Map<String, Object>> results = diagnosticService.pollSlider(driver, seconds);
        return ResponseDTO.success(results);
    }

    /**
     * 指纹检查 — 检查浏览器反爬检测状态
     *
     * @return 浏览器指纹信息
     */
    @GetMapping("/fingerprint")
    public ResponseDTO<Map<String, Object>> fingerprint() {
        log.info("检查浏览器指纹");
        WebDriver driver = browserSessionManager.getDriver();
        Map<String, Object> result = diagnosticService.checkFingerprint(driver);
        return ResponseDTO.success(result);
    }

    /**
     * 当前 frame 信息
     *
     * @return frame 上下文信息
     */
    @GetMapping("/frame-info")
    public ResponseDTO<Map<String, Object>> frameInfo() {
        log.info("查询 frame 信息");
        WebDriver driver = browserSessionManager.getDriver();
        Map<String, Object> info = diagnosticService.getFrameInfo(driver);
        return ResponseDTO.success(info);
    }

    /**
     * 执行任意 JS 脚本（仅开发调试用，生产环境应禁用）
     *
     * @param script JavaScript 代码
     * @return 执行结果
     */
    @PostMapping("/exec-js")
    public ResponseDTO<Object> execJs(@RequestBody String script) {
        log.info("执行 JS 脚本");
        WebDriver driver = browserSessionManager.getDriver();
        Object result = diagnosticService.executeJavaScript(driver, script);
        return ResponseDTO.success(result);
    }
}
