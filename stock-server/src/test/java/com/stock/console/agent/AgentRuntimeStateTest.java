// AI_GENERATE_START -
package com.stock.console.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeStateTest {

    @Test
    void keepsConversationOnlyInTheCurrentProcess() {
        AgentConversationStore conversations = new AgentConversationStore();
        String sessionId = String.valueOf(conversations.createSession("收益口径").get("id"));

        conversations.appendMessage(sessionId, "user", "只看净收益", null);

        assertEquals("只看净收益", conversations.messages(sessionId).get(0).get("content"));
        assertTrue(new AgentConversationStore().sessions().isEmpty());
    }

    @Test
    void masksCredentialsWithoutChangingOrdinaryBusinessValues() {
        SensitiveDataMasker masker = new SensitiveDataMasker();

        String masked = masker.mask("token=ghp_abcdefghijklmnopqrstuvwxyz password: secret123 net_profit=150");

        assertFalse(masked.contains("ghp_abcdefghijklmnopqrstuvwxyz"));
        assertFalse(masked.contains("secret123"));
        assertTrue(masked.contains("net_profit=150"));
    }
}
// AI_GENERATE_END -
