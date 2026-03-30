package com.stock.autoLogin.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.stock.autoLogin.dto.CaptchaTestCase;
import com.stock.autoLogin.dto.CaptchaTestCaseResult;
import com.stock.autoLogin.dto.CaptchaTestReport;
import com.stock.autoLogin.dto.CaptchaTestRunRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 验证码识别测试服务
 *
 * @author mwangli
 * @since 2026-03-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaRecognitionTestService {

    private final MathCaptchaService mathCaptchaService;

    @Value("${spring.auto-login.account:13278828091}")
    private String defaultAccount;

    private static final String TEST_BASE_DIR = ".tmp/captcha-test";
    private static final String CASES_DIR = TEST_BASE_DIR + "/cases";
    private static final String SCREENSHOTS_DIR = TEST_BASE_DIR + "/screenshots";
    private static final String REPORTS_DIR = TEST_BASE_DIR + "/reports";

    /**
     * 执行验证码识别测试
     *
     * @param request 测试请求参数
     * @return 测试报告
     */
    public CaptchaTestReport runTest(CaptchaTestRunRequest request) {
        String reportId = generateReportId();
        String account = request.getAccount() != null ? request.getAccount() : defaultAccount;
        CaptchaTestReport report = CaptchaTestReport.empty(reportId, account);

        log.info("开始执行验证码识别测试，reportId={}, account={}, saveScreenshots={}",
                reportId, account, request.isSaveScreenshots());

        List<CaptchaTestCase> testCases = getTargetTestCases(request.getCaseIds());

        for (CaptchaTestCase testCase : testCases) {
            CaptchaTestCaseResult result = executeTestCase(account, testCase, request.isSaveScreenshots(), reportId);
            report.addCaseResult(result);
            if (result.getScreenshotPath() != null) {
                report.addScreenshotPath(result.getScreenshotPath());
            }
            log.info("测试用例 {} 执行完成，passed={}, expression={}, result={}",
                    testCase.getCaseId(), result.isPassed(), result.getExpression(), result.getActualAnswer());
        }

        report.calculateStatistics();
        saveReport(report);

        log.info("验证码识别测试完成，reportId={}, total={}, passed={}, accuracy={:.2f}%",
                reportId, report.getTotalCount(), report.getPassedCount(), report.getAccuracy() * 100);

        return report;
    }

    /**
     * 执行单个测试用例
     */
    private CaptchaTestCaseResult executeTestCase(String account, CaptchaTestCase testCase,
            boolean saveScreenshots, String reportId) {
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> captchaResult = mathCaptchaService.getCaptchaResult(account);
            long elapsedTime = System.currentTimeMillis() - startTime;

            boolean success = (boolean) captchaResult.getOrDefault("success", false);

            if (!success) {
                return CaptchaTestCaseResult.fail(
                        testCase.getCaseId(),
                        testCase.getExpectedAnswer(),
                        (String) captchaResult.getOrDefault("error", "识别失败"),
                        elapsedTime
                );
            }

            String expression = (String) captchaResult.getOrDefault("expression", "");
            String actualAnswer = (String) captchaResult.getOrDefault("result", "");
            String method = (String) captchaResult.getOrDefault("method", "unknown");

            String screenshotPath = null;
            if (saveScreenshots) {
                screenshotPath = saveScreenshot(testCase.getCaseId(), reportId, account, captchaResult);
            }

            boolean passed = compareResult(actualAnswer, testCase.getExpectedAnswer());

            CaptchaTestCaseResult result = CaptchaTestCaseResult.pass(
                    testCase.getCaseId(),
                    expression,
                    testCase.getExpectedAnswer(),
                    actualAnswer,
                    method,
                    elapsedTime
            );
            result.setScreenshotPath(screenshotPath);

            return result;

        } catch (Exception e) {
            log.error("执行测试用例 {} 异常", testCase.getCaseId(), e);
            return CaptchaTestCaseResult.fail(
                    testCase.getCaseId(),
                    testCase.getExpectedAnswer(),
                    e.getMessage(),
                    System.currentTimeMillis() - startTime
            );
        }
    }

    /**
     * 保存验证码截图和识别结果
     */
    private String saveScreenshot(String caseId, String reportId, String account,
            Map<String, Object> captchaResult) {
        try {
            Path screenshotDir = Paths.get(SCREENSHOTS_DIR, reportId);
            Files.createDirectories(screenshotDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss_SSS"));
            String baseName = caseId + "_" + timestamp;

            // 1. 保存原始图片
            BufferedImage image = mathCaptchaService.fetchCaptchaImage(account);
            String imagePath = null;
            if (image != null) {
                Path imgPath = screenshotDir.resolve(baseName + "_raw.png");
                ImageIO.write(image, "PNG", imgPath.toFile());
                imagePath = imgPath.toString();
                log.info("截图已保存: {}", imgPath);
            }

            // 2. 保存识别结果
            Path resultPath = screenshotDir.resolve(baseName + "_result.json");
            String resultJson = JSON.toJSONString(captchaResult);
            Files.writeString(resultPath, resultJson, StandardCharsets.UTF_8);
            log.info("识别结果已保存: {}", resultPath);

            // 3. 保存人类可读的验证文件
            Path verifyPath = screenshotDir.resolve(baseName + "_verify.txt");
            String verifyContent = String.format(
                    "========================================%n" +
                    "验证码识别验证报告%n" +
                    "========================================%n" +
                    "时间: %s%n" +
                    "用例ID: %s%n" +
                    "----------------------------------------%n" +
                    "识别结果:%n" +
                    "  - 成功: %s%n" +
                    "  - 表达式: %s%n" +
                    "  - 计算结果: %s%n" +
                    "  - 识别方法: %s%n" +
                    "----------------------------------------%n" +
                    "原始图片: %s%n" +
                    "========================================",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    caseId,
                    captchaResult.getOrDefault("success", false),
                    captchaResult.getOrDefault("expression", "N/A"),
                    captchaResult.getOrDefault("result", "N/A"),
                    captchaResult.getOrDefault("method", "N/A"),
                    imagePath != null ? new File(imagePath).getName() : "N/A"
            );
            Files.writeString(verifyPath, verifyContent, StandardCharsets.UTF_8);
            log.info("验证报告已保存: {}", verifyPath);

            return imagePath;
        } catch (Exception e) {
            log.warn("保存截图失败 caseId={}: {}", caseId, e.getMessage());
            return null;
        }
    }

    /**
     * 对比识别结果与预期答案
     * 当预期答案为空时，无法验证，返回false（需要人工确认）
     */
    private boolean compareResult(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        String actualTrim = actual.trim();
        String expectedTrim = expected.trim();
        if (expectedTrim.isEmpty()) {
            return false;
        }
        return actualTrim.equals(expectedTrim);
    }

    /**
     * 获取目标测试用例列表
     */
    private List<CaptchaTestCase> getTargetTestCases(List<String> caseIds) {
        List<CaptchaTestCase> allCases = getAllTestCases();
        if (caseIds == null || caseIds.isEmpty()) {
            return allCases;
        }
        return allCases.stream()
                .filter(c -> caseIds.contains(c.getCaseId()))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有测试用例
     */
    public List<CaptchaTestCase> getAllTestCases() {
        try {
            Path casesFile = Paths.get(CASES_DIR, "test-cases.json");
            if (Files.exists(casesFile)) {
                String json = Files.readString(casesFile);
                return JSON.parseArray(json, CaptchaTestCase.class);
            }
        } catch (Exception e) {
            log.warn("加载测试用例文件失败: {}", e.getMessage());
        }
        List<CaptchaTestCase> predefinedCases = CaptchaTestCase.getPredefinedCases();
        saveTestCases(predefinedCases);
        return predefinedCases;
    }

    /**
     * 保存测试用例到文件
     */
    public void saveTestCases(List<CaptchaTestCase> testCases) {
        try {
            Files.createDirectories(Paths.get(CASES_DIR));
            Path casesFile = Paths.get(CASES_DIR, "test-cases.json");
            String json = JSON.toJSONString(testCases);
            Files.writeString(casesFile, json, StandardCharsets.UTF_8);
            log.info("测试用例已保存: {}", casesFile);
        } catch (IOException e) {
            log.error("保存测试用例失败", e);
        }
    }

    /**
     * 添加测试用例
     */
    public CaptchaTestCase addTestCase(CaptchaTestCase testCase) {
        List<CaptchaTestCase> allCases = getAllTestCases();
        allCases.removeIf(c -> c.getCaseId().equals(testCase.getCaseId()));
        allCases.add(testCase);
        saveTestCases(allCases);
        log.info("测试用例已添加: {}", testCase.getCaseId());
        return testCase;
    }

    /**
     * 根据ID获取测试用例
     */
    public CaptchaTestCase getTestCaseById(String caseId) {
        return getAllTestCases().stream()
                .filter(c -> c.getCaseId().equals(caseId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 生成测试报告
     */
    public void saveReport(CaptchaTestReport report) {
        try {
            Files.createDirectories(Paths.get(REPORTS_DIR));
            Path reportFile = Paths.get(REPORTS_DIR, report.getReportId() + ".json");
            String json = JSON.toJSONString(report);
            Files.writeString(reportFile, json, StandardCharsets.UTF_8);
            log.info("测试报告已保存: {}", reportFile);
        } catch (IOException e) {
            log.error("保存测试报告失败", e);
        }
    }

    /**
     * 加载测试报告
     */
    public CaptchaTestReport loadReport(String reportId) {
        try {
            Path reportFile = Paths.get(REPORTS_DIR, reportId + ".json");
            if (Files.exists(reportFile)) {
                String json = Files.readString(reportFile);
                return JSON.parseObject(json, CaptchaTestReport.class);
            }
        } catch (Exception e) {
            log.error("加载测试报告失败 reportId={}", reportId, e);
        }
        return null;
    }

    /**
     * 获取所有测试报告列表
     */
    public List<CaptchaTestReport> getAllReports() {
        List<CaptchaTestReport> reports = new ArrayList<>();
        try {
            Path reportsDir = Paths.get(REPORTS_DIR);
            if (Files.exists(reportsDir)) {
                Files.list(reportsDir)
                        .filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> {
                            try {
                                String json = Files.readString(p);
                                CaptchaTestReport report = JSON.parseObject(json, CaptchaTestReport.class);
                                reports.add(report);
                            } catch (Exception e) {
                                log.warn("加载报告文件失败: {}", p);
                            }
                        });
            }
        } catch (IOException e) {
            log.error("获取报告列表失败", e);
        }
        reports.sort((a, b) -> b.getGeneratedAt().compareTo(a.getGeneratedAt()));
        return reports;
    }

    /**
     * 生成唯一报告ID
     */
    private String generateReportId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "report_" + timestamp + "_" + uuid;
    }

    /**
     * 获取截图文件路径
     */
    public Path getScreenshotPath(String caseId, String reportId) {
        try {
            Path screenshotDir = Paths.get(SCREENSHOTS_DIR, reportId);
            if (Files.exists(screenshotDir)) {
                return Files.list(screenshotDir)
                        .filter(p -> p.getFileName().toString().startsWith(caseId + "_"))
                        .findFirst()
                        .orElse(null);
            }
        } catch (IOException e) {
            log.error("获取截图路径失败 caseId={}, reportId={}", caseId, reportId, e);
        }
        return null;
    }
}
