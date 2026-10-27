// AI_GENERATE_START----
package com.stock.data.service;

import com.stock.data.dto.NewsNormalizedDocument;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * 新闻标题和正文规范化组件。
 * 负责移除采集格式噪声，同时保留原始文档供审计和规则重放。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
public class NewsDocumentNormalizer {

    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile(
            "(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern HTML_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern DUPLICATE_TITLE_PREFIX = Pattern.compile(
            "^\\s*([^:：\\n]{2,30})[:：]\\s*\\1[:：]\\s*");
    private static final Pattern PAGE_LINE_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:第?\\s*\\d+\\s*页(?:\\s*共\\s*\\d+\\s*页)?|\\d+\\s*/\\s*\\d+)\\s*$");
    private static final Pattern DISCLOSURE_BOILERPLATE_PATTERN = Pattern.compile(
            "(?:本公司及(?:董事会|监事会)(?:全体)?成员保证[^。！？]{0,160}(?:重大遗漏|承担法律责任)[。！？]?)");
    private static final Pattern DISCLAIMER_PATTERN = Pattern.compile(
            "(?:免责声明[:：][^。！？\\n]{0,240}[。！？]?|本公告内容仅供参考[^。！？\\n]{0,120}[。！？]?"
                    + "|敬请(?:广大)?投资者(?:理性投资并)?注意投资风险[。！？]?"
                    + "|投资有风险[，,]?入市需谨慎[。！？]?)");
    private static final Pattern SPACE_PATTERN = Pattern.compile("[\\t\\x0B\\f\\r ]+");
    private static final Pattern BLANK_LINE_PATTERN = Pattern.compile("\\n{3,}");

    /**
     * 规范化新闻标题和正文。
     *
     * @param rawTitle 供应商原始标题
     * @param rawContent 供应商原始正文
     * @return 不修改原始值的规范化结果
     */
    public NewsNormalizedDocument normalize(String rawTitle, String rawContent) {
        String title = normalizeCommon(rawTitle);
        String previous;
        do {
            previous = title;
            title = DUPLICATE_TITLE_PREFIX.matcher(title).replaceFirst("$1：");
        } while (!title.equals(previous));

        String content = normalizeCommon(rawContent);
        content = PAGE_LINE_PATTERN.matcher(content).replaceAll("");
        content = DISCLOSURE_BOILERPLATE_PATTERN.matcher(content).replaceAll(" ");
        content = DISCLAIMER_PATTERN.matcher(content).replaceAll("");
        content = SPACE_PATTERN.matcher(content).replaceAll(" ");
        content = BLANK_LINE_PATTERN.matcher(content).replaceAll("\n\n").trim();
        return new NewsNormalizedDocument(title, content);
    }

    private String normalizeCommon(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        normalized = SCRIPT_STYLE_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = HTML_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = URL_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = normalized.replace("\r\n", "\n").replace('\r', '\n');
        normalized = SPACE_PATTERN.matcher(normalized).replaceAll(" ");
        return normalized.trim();
    }
}
// AI_GENERATE_END----
