// AI_GENERATE_START ---
package com.stock.console.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentServiceInputTest {

    /**
     * 验证普通问题不会被截断或改写。
     */
    @Test void keepsOrdinaryQuestionUnchanged() {
        AgentService.QuestionInput input = AgentService.normalizeQuestion("净收益是多少？");

        assertEquals("净收益是多少？", input.content());
        assertFalse(input.truncated());
    }

    /**
     * 验证超长问题保留首尾关键内容并标记截断。
     */
    @Test void truncatesLongInputWhileKeepingHeadAndTail() {
        String question = "HEAD" + "x".repeat(5_000) + "ERROR root failure";

        AgentService.QuestionInput input = AgentService.normalizeQuestion(question);

        assertTrue(input.truncated());
        assertTrue(input.content().startsWith("HEAD"));
        assertTrue(input.content().endsWith("ERROR root failure"));
        assertTrue(input.notice().contains("输入已截断"));
    }

    /**
     * 验证异常摘要选取最深层信息并移除换行噪声。
     */
    @Test void reportsTheDeepestProviderFailureWithoutMultilineNoise() {
        RuntimeException failure = new RuntimeException(
                "provider failed",
                new IllegalStateException("HTTP 401\ninvalid api key"));

        String detail = AgentService.providerFailureDetail(failure);

        assertEquals("IllegalStateException: HTTP 401 invalid api key", detail);
    }

    /**
     * 验证空来源默认归一化为电脑端来源。
     */
    @Test void defaultsBlankResourceToComputer() {
        assertEquals(AgentService.RESOURCE_COMPUTER, AgentService.normalizeResource(null));
        assertEquals(AgentService.RESOURCE_COMPUTER, AgentService.normalizeResource(" "));
    }

    /**
     * 验证移动端来源忽略首尾空白和大小写差异。
     */
    @Test void normalizesMobileResource() {
        assertEquals(AgentService.RESOURCE_MOBILE, AgentService.normalizeResource(" MOBILE "));
    }

    /**
     * 验证不支持的来源会被明确拒绝。
     */
    @Test void rejectsUnsupportedResource() {
        assertThrows(IllegalArgumentException.class, () -> AgentService.normalizeResource("tablet"));
    }
}
// AI_GENERATE_END ---
