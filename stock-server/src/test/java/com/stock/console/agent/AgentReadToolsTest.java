// AI_GENERATE_START --
package com.stock.console.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.console.config.ConsoleProperties;
import com.stock.console.facts.TradingFacts;
import com.stock.console.market.MarketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentReadToolsTest {

    @TempDir
    Path tempDir;

    private TradingFacts facts;
    private MarketRepository market;
    private AgentToolAudit audit;
    private AgentReadTools tools;

    @BeforeEach
    void setUp() {
        facts = mock(TradingFacts.class);
        market = mock(MarketRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ConsoleProperties properties = properties();
        AgentDatabase database = new AgentDatabase(properties);
        database.initialize();
        SensitiveDataMasker masker = new SensitiveDataMasker();
        audit = new AgentToolAudit(database, objectMapper, masker);
        tools = new AgentReadTools(
                facts, market, objectMapper, audit,
                new AgentMemoryStore(database, properties), masker);
        audit.begin("test-session");
    }

    @Test
    void returnsDeterministicPerformanceSummaryWithSource() {
        Map<String, Object> summary = Map.of(
                "groupBy", "month",
                "rows", List.of(Map.of("period", "2026-10", "net_profit", 120.5)));
        when(facts.agentPerformanceSummary(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "month"))
                .thenReturn(summary);

        String result = tools.performanceSummary("2026-10-01", "2026-10-31", "month");

        assertTrue(result.contains("2026-10"));
        assertEquals(List.of("sqlite:realized_trade:performance"), audit.finish());
        verify(facts).agentPerformanceSummary(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "month");
    }

    @Test
    void rejectsInvalidDateRangeAndUnsupportedGrouping() {
        assertThrows(IllegalArgumentException.class,
                () -> tools.performanceSummary("2026-11-02", "2026-11-01", "total"));
        assertThrows(IllegalArgumentException.class,
                () -> tools.performanceSummary("2026-11-01", "2026-11-02", "sql"));
    }

    @Test
    void capsDecisionEvidenceToReadOnlyExistingCandidates() {
        when(facts.agentPredictionRanking(null, 20)).thenReturn(List.of(Map.of(
                "instrument", "sh.600000", "rank", 1)));
        when(facts.tradingStatus()).thenReturn(Map.of("available", true));
        when(facts.models(5)).thenReturn(List.of());
        when(facts.evaluations(5)).thenReturn(List.of());
        when(market.statistics()).thenReturn(Map.of("available", true));

        String result = tools.tradeDecisionEvidence(20);

        assertTrue(result.contains("ADVISORY_ONLY"));
        assertTrue(result.contains("sh.600000"));
        assertTrue(result.contains("不得新增证券"));
        assertEquals(List.of("sqlite+parquet:trade-decision-evidence"), audit.finish());
        assertThrows(IllegalArgumentException.class, () -> tools.tradeDecisionEvidence(21));
    }

    @Test
    void validatesToolResultLimits() {
        assertThrows(IllegalArgumentException.class, () -> tools.taskFailures(0));
        assertThrows(IllegalArgumentException.class, () -> tools.predictionRanking(null, 201));
        assertThrows(IllegalArgumentException.class, () -> tools.realizedTradeDetail(null, null, null, 201));
    }

    private ConsoleProperties properties() {
        return new ConsoleProperties(
                Path.of("data"),
                Path.of("logs/tasks.log"),
                "documents",
                new ConsoleProperties.Auth("admin", "hash"),
                new ConsoleProperties.Agent(
                        true, tempDir, "https://api.example.com", "key", "model", 90, 1800),
                new ConsoleProperties.GithubAgent(
                        false, "", "", "", "stock-web", "ci-cd.yml", 2, 2, 5));
    }
}
// AI_GENERATE_END --
