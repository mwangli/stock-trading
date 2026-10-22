// AI_GENERATE_START ---
package com.stock.modelService.service;

import com.stock.dataCollector.service.NewsEventClassifier;
import com.stock.modelService.domain.vo.SentimentAggregateResult;
import com.stock.modelService.domain.vo.SentimentAnalysisResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 情感业务影响规则测试。
 *
 * @author mwangli
 * @since 2026-10-22
 */
class SentimentAggregationServiceTest {

    private final SentimentAggregationService service =
            new SentimentAggregationService(null, new NewsEventClassifier());

    @Test
    void shouldNotBlockNeutralDirectorDeclarationContainingRiskChecklist() {
        String text = "爱丽家居:独立董事提名人声明与承诺\n\n"
                + "被提名人无下列不良记录：因涉嫌证券期货违法犯罪，"
                + "被中国证监会立案调查或者被司法机关立案侦查。";

        SentimentAggregateResult result = service.assessText(text, "新闻", modelResult("neutral", 0.0036D));

        assertThat(result.isStrongNegative()).isFalse();
        assertThat(result.getStrategyAction()).isEqualTo("NO_ACTION");
        assertThat(result.getDominantEventType()).isEqualTo("SHAREHOLDER_OR_EXECUTIVE");
    }

    @Test
    void shouldBlockConfirmedStrongNegativeInvestigation() {
        String text = "公司收到中国证监会立案调查通知\n\n"
                + "公司因涉嫌信息披露重大违法被中国证监会立案调查。";

        SentimentAggregateResult result = service.assessText(text, "公告", modelResult("negative", -0.85D));

        assertThat(result.isStrongNegative()).isTrue();
        assertThat(result.getStrategyAction()).isEqualTo("RISK_BLOCK");
        assertThat(result.getNewsImpactScore()).isEqualTo(-0.8D);
        assertThat(result.getDominantEventType()).isEqualTo("REGULATORY_OR_LITIGATION");
    }

    @Test
    void shouldNotBlockNegatedRiskStatementEvenWhenModelIsNegative() {
        String text = "公司风险事项澄清公告\n\n公司不存在被立案调查或重大违法的情形。";

        SentimentAggregateResult result = service.assessText(text, "公告", modelResult("negative", -0.85D));

        assertThat(result.isStrongNegative()).isFalse();
        assertThat(result.getStrategyAction()).isEqualTo("REDUCE");
    }

    @Test
    void shouldBlockConfirmedRiskAfterNegationReversal() {
        String text = "公司风险事项进展公告\n\n"
                + "公司此前不存在被立案调查情形，但近日收到中国证监会立案调查通知。";

        SentimentAggregateResult result = service.assessText(text, "公告", modelResult("negative", -0.85D));

        assertThat(result.isStrongNegative()).isTrue();
        assertThat(result.getStrategyAction()).isEqualTo("RISK_BLOCK");
    }

    private SentimentAnalysisResult modelResult(String label, double score) {
        return SentimentAnalysisResult.builder()
                .label(label)
                .score(score)
                .confidence(0.95D)
                .build();
    }
}
// AI_GENERATE_END ---
