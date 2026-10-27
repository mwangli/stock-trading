// AI_GENERATE_START -
package com.stock.model.service;

import com.stock.model.dto.SentimentModelInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 情感模型输入组装服务。
 * 标题始终进入首块，正文优先选择包含财务、监管、风险和反转信息的关键句，最多生成三个分块。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Service
public class SentimentInputAssembler {

    private static final int MAX_CHUNKS = 3;
    private static final int MAX_CHARS_PER_CHUNK = 420;
    private static final Pattern SENTENCE_SEPARATOR = Pattern.compile("(?<=[。！？!?；;\\n])");
    private static final List<String> KEY_TERMS = List.of(
            "增长", "下降", "盈利", "亏损", "预增", "预减", "回购", "增持", "减持",
            "分红", "中标", "合同", "重组", "并购", "诉讼", "处罚", "立案", "监管",
            "退市", "违约", "冻结", "质押", "风险", "但是", "然而", "不构成", "不存在");

    /**
     * 使用明确标题和正文建立模型输入。
     *
     * @param title 新闻或公告标题
     * @param content 新闻或公告正文
     * @return 最多三块的统一模型输入
     */
    public SentimentModelInput assemble(String title, String content) {
        String safeTitle = normalize(title);
        String safeContent = normalize(content);
        List<String> sentences = splitSentences(safeContent);
        Set<String> selected = new LinkedHashSet<>();
        sentences.stream().filter(this::isKeySentence).forEach(selected::add);
        sentences.forEach(selected::add);

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        append(current, safeTitle);
        for (String sentence : selected) {
            if (chunks.size() >= MAX_CHUNKS) {
                break;
            }
            if (current.length() > 0 && current.length() + sentence.length() + 1 > MAX_CHARS_PER_CHUNK) {
                chunks.add(current.toString());
                current = new StringBuilder();
                append(current, safeTitle);
            }
            append(current, sentence);
        }
        if (current.length() > 0 && chunks.size() < MAX_CHUNKS) {
            chunks.add(current.toString());
        }
        if (chunks.isEmpty()) {
            chunks = List.of("");
        }

        int originalCharacters = safeTitle.length() + safeContent.length();
        int includedCharacters = chunks.stream().mapToInt(String::length).sum()
                - Math.max(0, chunks.size() - 1) * safeTitle.length();
        double coverage = originalCharacters == 0 ? 0D
                : Math.min(1D, includedCharacters / (double) originalCharacters);
        return SentimentModelInput.builder()
                .title(safeTitle)
                .content(safeContent)
                .chunks(List.copyOf(chunks))
                .originalCharacterCount(originalCharacters)
                .includedCharacterCount(Math.max(0, includedCharacters))
                .textCoverage(coverage)
                .build();
    }

    /**
     * 将无独立标题的历史调用组装为统一输入。
     *
     * @param text 原始文本
     * @return 统一模型输入
     */
    public SentimentModelInput assembleText(String text) {
        return assemble("", text);
    }

    private List<String> splitSentences(String content) {
        if (content.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : SENTENCE_SEPARATOR.split(content)) {
            String sentence = value.trim();
            if (!sentence.isBlank()) {
                result.add(sentence);
            }
        }
        return result;
    }

    private boolean isKeySentence(String sentence) {
        return KEY_TERMS.stream().anyMatch(sentence::contains);
    }

    private void append(StringBuilder target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (target.length() > 0) {
            target.append('。');
        }
        int remaining = MAX_CHARS_PER_CHUNK - target.length();
        if (remaining > 0) {
            target.append(value, 0, Math.min(value.length(), remaining));
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace('\u0000', ' ').replaceAll("\\s+", " ").trim();
    }
}
// AI_GENERATE_END -
