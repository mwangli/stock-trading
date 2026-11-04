// AI_GENERATE_START --
package com.stock.console.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentServiceInputTest {

    @Test
    void keepsOrdinaryQuestionUnchanged() {
        AgentService.QuestionInput input = AgentService.normalizeQuestion("净收益是多少？");

        assertEquals("净收益是多少？", input.content());
        assertFalse(input.truncated());
    }

    @Test
    void truncatesLongInputWhileKeepingHeadAndTail() {
        String question = "HEAD" + "x".repeat(5_000) + "ERROR root failure";

        AgentService.QuestionInput input = AgentService.normalizeQuestion(question);

        assertTrue(input.truncated());
        assertTrue(input.content().startsWith("HEAD"));
        assertTrue(input.content().endsWith("ERROR root failure"));
        assertTrue(input.notice().contains("输入已截断"));
    }

    @Test
    void reportsTheDeepestProviderFailureWithoutMultilineNoise() {
        RuntimeException failure = new RuntimeException(
                "provider failed",
                new IllegalStateException("HTTP 401\ninvalid api key"));

        String detail = AgentService.providerFailureDetail(failure);

        assertEquals("IllegalStateException: HTTP 401 invalid api key", detail);
    }
}
// AI_GENERATE_END --
