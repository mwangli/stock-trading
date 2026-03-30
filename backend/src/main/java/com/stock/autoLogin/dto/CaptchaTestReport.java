package com.stock.autoLogin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 验证码识别测试报告
 *
 * @author mwangli
 * @since 2026-03-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaTestReport {

    /**
     * 报告唯一标识
     */
    private String reportId;

    /**
     * 报告生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 测试账号
     */
    private String account;

    /**
     * 总测试用例数
     */
    private int totalCount;

    /**
     * 通过数
     */
    private int passedCount;

    /**
     * 失败数
     */
    private int failedCount;

    /**
     * 准确率（通过数/总数）
     */
    private double accuracy;

    /**
     * 错误率（失败数/总数）
     */
    private double errorRate;

    /**
     * 测试用例执行结果列表
     */
    private List<CaptchaTestCaseResult> caseResults;

    /**
     * 截图文件路径列表
     */
    private List<String> screenshotPaths;

    /**
     * 总耗时（毫秒）
     */
    private long totalElapsedTimeMs;

    /**
     * 创建空报告
     */
    public static CaptchaTestReport empty(String reportId, String account) {
        CaptchaTestReport report = new CaptchaTestReport();
        report.setReportId(reportId);
        report.setGeneratedAt(LocalDateTime.now());
        report.setAccount(account);
        report.setTotalCount(0);
        report.setPassedCount(0);
        report.setFailedCount(0);
        report.setAccuracy(0.0);
        report.setErrorRate(0.0);
        report.setCaseResults(new ArrayList<>());
        report.setScreenshotPaths(new ArrayList<>());
        report.setTotalElapsedTimeMs(0);
        return report;
    }

    /**
     * 计算统计指标
     */
    public void calculateStatistics() {
        if (totalCount > 0) {
            this.accuracy = (double) passedCount / totalCount;
            this.errorRate = (double) failedCount / totalCount;
        }
    }

    /**
     * 添加测试结果
     */
    public void addCaseResult(CaptchaTestCaseResult result) {
        if (this.caseResults == null) {
            this.caseResults = new ArrayList<>();
        }
        this.caseResults.add(result);
        this.totalCount++;
        if (result.isPassed()) {
            this.passedCount++;
        } else {
            this.failedCount++;
        }
        this.totalElapsedTimeMs += result.getElapsedTimeMs();
    }

    /**
     * 添加截图路径
     */
    public void addScreenshotPath(String path) {
        if (this.screenshotPaths == null) {
            this.screenshotPaths = new ArrayList<>();
        }
        this.screenshotPaths.add(path);
    }
}
