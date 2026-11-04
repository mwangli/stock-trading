// AI_GENERATE_START -
package com.stock.console.agent;

import com.stock.console.config.ConsoleProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevelopmentTaskServiceTest {

    @Test
    void returnsEmptyHistoryWhenGithubAgentIsNotConfigured() {
        GitHubDevelopmentClient github = mock(GitHubDevelopmentClient.class);
        DevelopmentTaskStore store = mock(DevelopmentTaskStore.class);
        when(store.recent(20)).thenReturn(List.of());
        when(github.configured()).thenReturn(false);
        DevelopmentTaskService service = new DevelopmentTaskService(
                mock(ConsoleProperties.class), github, mock(DevelopmentModelService.class), store);

        assertTrue(service.recent().isEmpty());
        verify(github, never()).recentTaskCommits(20);
    }
}
// AI_GENERATE_END -
