package com.stock.autoLogin.controller;

import com.stock.autoLogin.dto.CaptchaTestCase;
import com.stock.autoLogin.dto.CaptchaTestReport;
import com.stock.autoLogin.dto.CaptchaTestRunRequest;
import com.stock.autoLogin.service.CaptchaRecognitionTestService;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 验证码识别测试控制器
 *
 * @author mwangli
 * @since 2026-03-30
 */
@Slf4j
@RestController
@RequestMapping("/api/test/captcha")
@RequiredArgsConstructor
public class CaptchaTestController {

    private final CaptchaRecognitionTestService captchaRecognitionTestService;

    @Value("${spring.auto-login.account:13278828091}")
    private String defaultAccount;

    /**
     * 执行验证码识别测试
     *
     * @param request 测试请求参数
     * @return 测试报告
     */
    @PostMapping("/run")
    public ResponseDTO<CaptchaTestReport> runTest(@RequestBody(required = false) CaptchaTestRunRequest request) {
        log.info("收到执行测试请求");

        if (request == null) {
            request = CaptchaTestRunRequest.defaultRequest(defaultAccount);
        } else {
            if (request.getAccount() == null || request.getAccount().isEmpty()) {
                request.setAccount(defaultAccount);
            }
            request.setSaveScreenshots(true);
        }

        CaptchaTestReport report = captchaRecognitionTestService.runTest(request);
        return ResponseDTO.success(report, "测试执行完成");
    }

    /**
     * 添加测试用例
     *
     * @param testCase 测试用例
     * @return 添加的测试用例
     */
    @PostMapping("/add-case")
    public ResponseDTO<CaptchaTestCase> addTestCase(@RequestBody CaptchaTestCase testCase) {
        log.info("添加测试用例: caseId={}", testCase.getCaseId());
        CaptchaTestCase saved = captchaRecognitionTestService.addTestCase(testCase);
        return ResponseDTO.success(saved, "测试用例添加成功");
    }

    /**
     * 获取测试报告
     *
     * @param reportId 报告ID
     * @return 测试报告
     */
    @GetMapping("/report/{reportId}")
    public ResponseDTO<CaptchaTestReport> getReport(@PathVariable String reportId) {
        log.info("获取测试报告: reportId={}", reportId);
        CaptchaTestReport report = captchaRecognitionTestService.loadReport(reportId);
        if (report == null) {
            return ResponseDTO.failure("报告不存在: " + reportId);
        }
        return ResponseDTO.success(report);
    }

    /**
     * 获取所有测试报告列表
     *
     * @return 报告列表
     */
    @GetMapping("/reports")
    public ResponseDTO<List<CaptchaTestReport>> getAllReports() {
        log.info("获取所有测试报告列表");
        List<CaptchaTestReport> reports = captchaRecognitionTestService.getAllReports();
        return ResponseDTO.success(reports);
    }

    /**
     * 获取指定用例的截图
     *
     * @param caseId   用例ID
     * @param reportId 报告ID（可选）
     * @return 截图文件
     */
    @GetMapping("/screenshot/{caseId}")
    public ResponseEntity<Resource> getScreenshot(
            @PathVariable String caseId,
            @RequestParam(required = false) String reportId) {
        log.info("获取截图: caseId={}, reportId={}", caseId, reportId);

        Path screenshotPath;
        if (reportId != null && !reportId.isEmpty()) {
            screenshotPath = captchaRecognitionTestService.getScreenshotPath(caseId, reportId);
        } else {
            screenshotPath = findLatestScreenshot(caseId);
        }

        if (screenshotPath == null || !screenshotPath.toFile().exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(screenshotPath.toFile());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }

    /**
     * 获取所有测试用例
     *
     * @return 测试用例列表
     */
    @GetMapping("/cases")
    public ResponseDTO<List<CaptchaTestCase>> getAllCases() {
        log.info("获取所有测试用例");
        List<CaptchaTestCase> cases = captchaRecognitionTestService.getAllTestCases();
        return ResponseDTO.success(cases);
    }

    /**
     * 获取指定测试用例
     *
     * @param caseId 用例ID
     * @return 测试用例
     */
    @GetMapping("/case/{caseId}")
    public ResponseDTO<CaptchaTestCase> getCase(@PathVariable String caseId) {
        log.info("获取测试用例: caseId={}", caseId);
        CaptchaTestCase testCase = captchaRecognitionTestService.getTestCaseById(caseId);
        if (testCase == null) {
            return ResponseDTO.failure("测试用例不存在: " + caseId);
        }
        return ResponseDTO.success(testCase);
    }

    /**
     * 查找最新截图路径
     */
    private Path findLatestScreenshot(String caseId) {
        List<CaptchaTestReport> reports = captchaRecognitionTestService.getAllReports();
        for (CaptchaTestReport report : reports) {
            Path path = captchaRecognitionTestService.getScreenshotPath(caseId, report.getReportId());
            if (path != null && path.toFile().exists()) {
                return path;
            }
        }
        return null;
    }
}
