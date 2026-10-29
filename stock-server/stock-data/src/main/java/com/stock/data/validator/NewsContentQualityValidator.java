// AI_GENERATE_START --
package com.stock.data.validator;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

/**
 * 新闻正文完整性校验器。
 * 用于识别空正文、仅标题副本和仅包含章节名称的截断内容，保护详情采集与行情展示质量。
 *
 * @author mwangli
 * @since 2026-10-28
 */
@Component
public class NewsContentQualityValidator {

    /** 实质正文去除空白和标点后允许的最小字符数。 */
    private static final int MINIMUM_CONTENT_LENGTH = 20;

    /**
     * 判断正文是否包含可消费的实质信息。
     *
     * @param title 新闻标题，可为空
     * @param content 新闻正文，可为空
     * @return 正文不是空值、标题副本或过短章节片段时返回 true
     */
    public boolean hasSubstantiveContent(String title, String content) {
        String compactContent = compact(content);
        if (compactContent.length() < MINIMUM_CONTENT_LENGTH) {
            return false;
        }
        String compactTitle = compact(title);
        return compactTitle.isEmpty() || !compactContent.equals(compactTitle);
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("(?is)<[^>]+>", "")
                .replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]+", "")
                .trim();
    }
}
// AI_GENERATE_END --
