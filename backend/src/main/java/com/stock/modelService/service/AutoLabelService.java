package com.stock.modelService.service;

import com.stock.modelService.config.SentimentEvaluationConfig;
import com.stock.modelService.domain.entity.SentimentAutoLabel;
import com.stock.modelService.persistence.SentimentAutoLabelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 情感模型自动标注服务
 *
 * <p>基于交易反馈数据（T+1 收益）自动生成情感标注，用于情感分析模型的半监督学习。
 * 标注规则：
 * <ul>
 *   <li>收益率 > 1% → 利好 (positive)</li>
 *   <li>收益率 -1% ~ 1% → 中性 (neutral)</li>
 *   <li>收益率 < -1% → 利空 (negative)</li>
 * </ul>
 * </p>
 *
 * <p>数据质量过滤：
 * <ul>
 *   <li>置信度 ≥ 0.6</li>
 *   <li>仅使用 T+1 实际成交的反馈</li>
 *   <li>相同文本仅保留一条标注</li>
 * </ul>
 * </p>
 *
 * <p>标注状态流转：pending → validated → used / discarded</p>
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoLabelService {

    private final SentimentAutoLabelRepository autoLabelRepository;
    private final SentimentEvaluationConfig config;

    /**
     * 标注状态：待验证
     */
    public static final String STATUS_PENDING = "pending";

    /**
     * 标注状态：已验证
     */
    public static final String STATUS_VALIDATED = "validated";

    /**
     * 标注状态：已使用
     */
    public static final String STATUS_USED = "used";

    /**
     * 标注状态：已丢弃
     */
    public static final String STATUS_DISCARDED = "discarded";

    /**
     * 标签：利好
     */
    public static final String LABEL_POSITIVE = "positive";

    /**
     * 标签：中性
     */
    public static final String LABEL_NEUTRAL = "neutral";

    /**
     * 标签：利空
     */
    public static final String LABEL_NEGATIVE = "negative";

    /**
     * 生成自动标注
     *
     * <p>基于交易反馈数据（T+1 收益）自动生成标注。
     * 执行流程：
     * <ol>
     *   <li>从交易反馈数据源获取 T+1 已结算的交易记录</li>
     *   <li>计算每笔交易的收益率</li>
     *   <li>根据标注规则生成标签和置信度</li>
     *   <li>过滤低置信度（< 0.6）和重复文本</li>
     *   <li>保存到 MongoDB，状态为 pending</li>
     *   <li>自动将 pending 状态更新为 validated</li>
     * </ol>
     * </p>
     *
     * @return 生成的标注数量
     */
    public int generateLabels() {
        log.info("开始生成自动标注");

        List<TradingFeedback> feedbackList = fetchTradingFeedback();
        if (feedbackList.isEmpty()) {
            log.warn("未获取到交易反馈数据");
            return 0;
        }

        log.info("获取到 {} 条交易反馈记录", feedbackList.size());

        Set<String> existingTexts = new HashSet<>();
        autoLabelRepository.findByStatus(STATUS_VALIDATED)
                .forEach(label -> existingTexts.add(label.getText()));

        int generatedCount = 0;
        for (TradingFeedback feedback : feedbackList) {
            String text = feedback.getText();
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            if (existingTexts.contains(text.trim())) {
                log.debug("文本已存在标注，跳过: {}", text.substring(0, Math.min(50, text.length())));
                continue;
            }

            double returnRate = calculateReturnRate(feedback);
            String label = determineLabel(returnRate);
            double confidence = calculateConfidence(returnRate);

            if (confidence < config.getMinConfidenceForLabel()) {
                log.debug("置信度低于阈值，跳过: returnRate={}, confidence={}", returnRate, confidence);
                continue;
            }

            SentimentAutoLabel autoLabel = new SentimentAutoLabel();
            autoLabel.setText(text.trim());
            autoLabel.setLabel(label);
            autoLabel.setConfidence(confidence);
            autoLabel.setSource("trading_feedback");
            autoLabel.setStockCode(feedback.getStockCode());
            autoLabel.setReturnRate(returnRate);
            autoLabel.setStatus(STATUS_PENDING);
            autoLabel.setCreatedAt(LocalDateTime.now());

            autoLabelRepository.save(autoLabel);
            existingTexts.add(text.trim());
            generatedCount++;
        }

        log.info("生成 {} 条自动标注，尝试将 pending 状态更新为 validated", generatedCount);
        int validatedCount = validatePendingLabels();

        return generatedCount;
    }

    /**
     * 将 pending 状态的标注更新为 validated
     *
     * @return 更新数量
     */
    private int validatePendingLabels() {
        List<SentimentAutoLabel> pendingLabels = autoLabelRepository
                .findByStatusAndConfidenceGreaterThanEqual(STATUS_PENDING, config.getMinConfidenceForLabel());

        if (pendingLabels.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (SentimentAutoLabel label : pendingLabels) {
            label.setStatus(STATUS_VALIDATED);
            autoLabelRepository.save(label);
            count++;
        }

        log.info("已将 {} 条标注从 pending 更新为 validated", count);
        return count;
    }

    /**
     * 获取待使用的标注数量
     *
     * <p>返回有效标注数量，即置信度 >= 0.6 且状态为 validated 的标注数量。</p>
     *
     * @return 有效标注数量
     */
    public long getAvailableLabelCount() {
        return autoLabelRepository.countByStatus(STATUS_VALIDATED);
    }

    /**
     * 获取指定状态的标注数量
     *
     * @param status 状态：pending / validated / used / discarded
     * @return 数量
     */
    public long getLabelCountByStatus(String status) {
        return autoLabelRepository.countByStatus(status);
    }

    /**
     * 从交易反馈数据源获取 T+1 已结算的交易记录
     *
     * <p>当前实现为模拟数据，实际使用时需对接真实的交易系统。
     * 子类可覆盖 {@link #fetchActualTradingFeedback()} 方法获取真实数据。</p>
     *
     * @return 交易反馈列表
     */
    protected List<TradingFeedback> fetchTradingFeedback() {
        return fetchActualTradingFeedback();
    }

    /**
     * 获取真实的交易反馈数据（子类可覆盖）
     *
     * <p>该方法为模板方法的钩子，子类实现此方法连接真实的交易系统，
     * 例如从订单记录、持仓记录中计算 T+1 收益。</p>
     *
     * <p>当前实现说明：
     * <ul>
     *   <li>系统无持久化交易记录，仅返回模拟数据用于演示</li>
     *   <li>TODO：后续对接真实交易系统时，需替换为以下逻辑：
     *     <ol>
     *       <li>查询 T+1 已卖出股票的持仓记录（HoldingRepository）</li>
     *       <li>获取买入成本和卖出价格</li>
     *       <li>计算收益率 = (卖出价 - 买入价) / 买入价 * 100</li>
     *       <li>关联新闻文本（通过 StockNewsRepository）</li>
     *       <li>返回 TradingFeedback 列表</li>
     *     </ol>
     *   </li>
     * </ul>
     * </p>
     *
     * @return 交易反馈列表
     */
    protected List<TradingFeedback> fetchActualTradingFeedback() {
        log.info("[AutoLabel] 交易数据对接：当前系统无持久化交易记录，使用模拟数据进行标注演示");
        log.info("[AutoLabel] TODO 后续对接真实交易系统时，替换 generateMockTradingFeedback() 为真实数据查询逻辑");

        return generateMockTradingFeedback();
    }

    /**
     * 生成模拟交易反馈数据（仅用于开发和演示）
     *
     * <p>生成符合标注规则的数据分布：
     * <ul>
     *   <li>约 1/3 正面案例（收益率 > 1%）</li>
     *   <li>约 1/3 中性案例（收益率 -1% ~ 1%）</li>
     *   <li>约 1/3 负面案例（收益率 < -1%）</li>
     * </ul>
     * </p>
     *
     * @return 模拟交易反馈列表
     */
    private List<TradingFeedback> generateMockTradingFeedback() {
        log.info("[AutoLabel] 生成模拟交易反馈数据，共 9 条示例数据");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);

        return List.of(
                createFeedback("600519", "贵州茅台发布年度业绩预告，营收和净利润均实现两位数增长",
                        1850.0, 1920.0, yesterday, now),
                createFeedback("000858", "五粮液渠道库存良性，春节动销表现良好",
                        168.0, 175.0, yesterday, now),
                createFeedback("601318", "中国平安科技板块分拆上市传闻持续发酵",
                        48.0, 50.2, yesterday, now),
                createFeedback("600036", "招商银行零售业务优势稳固，不良贷款率持续下降",
                        35.0, 36.5, yesterday, now),
                createFeedback("000333", "美的集团回购股份彰显管理层信心",
                        60.0, 61.8, yesterday, now),
                createFeedback("002475", "立讯精密受供应链影响，产能利用率小幅下滑",
                        28.0, 28.3, yesterday, now),
                createFeedback("600900", "长江电力来水量低于预期，上半年发电量同比下降",
                        22.0, 21.8, yesterday, now),
                createFeedback("601166", "兴业银行房地产贷款不良率有所上升，引发市场担忧",
                        17.0, 16.5, yesterday, now),
                createFeedback("600276", "恒瑞医药创新药管线丰富，但研发费用高企压缩利润空间",
                        45.0, 44.6, yesterday, now)
        );
    }

    /**
     * 创建单条交易反馈数据
     *
     * @param stockCode  股票代码
     * @param text       新闻文本
     * @param buyPrice   买入价格
     * @param sellPrice  卖出价格
     * @param signalDate 信号日期
     * @param sellDate   卖出日期
     * @return 交易反馈对象
     */
    private TradingFeedback createFeedback(String stockCode, String text,
                                            double buyPrice, double sellPrice,
                                            LocalDateTime signalDate, LocalDateTime sellDate) {
        double returnRate = calculateReturnRate(buyPrice, sellPrice);
        log.debug("[AutoLabel] 模拟数据 - {}: 买入价={}, 卖出价={}, 收益率={}%",
                stockCode, buyPrice, sellPrice, String.format("%.2f", returnRate));
        return new TradingFeedback(stockCode, text,
                BigDecimal.valueOf(buyPrice), BigDecimal.valueOf(sellPrice),
                signalDate, sellDate);
    }

    /**
     * 计算交易收益率（重载方法）
     *
     * @param buyPrice  买入价格
     * @param sellPrice 卖出价格
     * @return 收益率（百分比），例如 2.5 表示 2.5%
     */
    private double calculateReturnRate(double buyPrice, double sellPrice) {
        if (buyPrice <= 0) {
            return 0.0;
        }
        return (sellPrice - buyPrice) / buyPrice * 100;
    }

    /**
     * 计算交易收益率
     *
     * @param feedback 交易反馈
     * @return 收益率（百分比），例如 2.5 表示 2.5%
     */
    private double calculateReturnRate(TradingFeedback feedback) {
        if (feedback.getBuyPrice() == null || feedback.getSellPrice() == null) {
            return 0.0;
        }
        if (feedback.getBuyPrice().compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return feedback.getSellPrice()
                .subtract(feedback.getBuyPrice())
                .divide(feedback.getBuyPrice(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    /**
     * 根据收益率确定情感标签
     *
     * @param returnRate 收益率（百分比）
     * @return 标签：positive / neutral / negative
     */
    private String determineLabel(double returnRate) {
        if (returnRate > config.getPositiveReturnThreshold()) {
            return LABEL_POSITIVE;
        } else if (returnRate < config.getNegativeReturnThreshold()) {
            return LABEL_NEGATIVE;
        } else {
            return LABEL_NEUTRAL;
        }
    }

    /**
     * 根据收益率计算置信度
     *
     * <p>收益率绝对值越大，置信度越高。
     * 阈值：|returnRate| >= 3% 时置信度为 1.0，|returnRate| <= 1% 时置信度为 0.6。</p>
     *
     * @param returnRate 收益率（百分比）
     * @return 置信度 (0.6 ~ 1.0)
     */
    private double calculateConfidence(double returnRate) {
        double absReturn = Math.abs(returnRate);
        if (absReturn >= 3.0) {
            return 1.0;
        }
        double confidence = 0.6 + (absReturn - 1.0) / 2.0 * 0.4;
        return Math.min(1.0, Math.max(config.getMinConfidenceForLabel(), confidence));
    }

    /**
     * 交易反馈数据
     *
     * <p>封装交易系统反馈的 T+1 收益数据，包含股票代码、新闻文本、
     * 买卖价格以及交易日期等信息。</p>
     */
    protected static class TradingFeedback {

        /**
         * 股票代码
         */
        private String stockCode;

        /**
         * 新闻文本（用于情感标注）
         */
        private String text;

        /**
         * 买入价格
         */
        private BigDecimal buyPrice;

        /**
         * 卖出价格
         */
        private BigDecimal sellPrice;

        /**
         * 信号日期（买入信号产生的日期）
         */
        private LocalDateTime signalDate;

        /**
         * 卖出日期（T+1 实际卖出的日期）
         */
        private LocalDateTime sellDate;

        public TradingFeedback() {
        }

        public TradingFeedback(String stockCode, String text, BigDecimal buyPrice,
                               BigDecimal sellPrice, LocalDateTime signalDate, LocalDateTime sellDate) {
            this.stockCode = stockCode;
            this.text = text;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.signalDate = signalDate;
            this.sellDate = sellDate;
        }

        public String getStockCode() {
            return stockCode;
        }

        public void setStockCode(String stockCode) {
            this.stockCode = stockCode;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public BigDecimal getBuyPrice() {
            return buyPrice;
        }

        public void setBuyPrice(BigDecimal buyPrice) {
            this.buyPrice = buyPrice;
        }

        public BigDecimal getSellPrice() {
            return sellPrice;
        }

        public void setSellPrice(BigDecimal sellPrice) {
            this.sellPrice = sellPrice;
        }

        public LocalDateTime getSignalDate() {
            return signalDate;
        }

        public void setSignalDate(LocalDateTime signalDate) {
            this.signalDate = signalDate;
        }

        public LocalDateTime getSellDate() {
            return sellDate;
        }

        public void setSellDate(LocalDateTime sellDate) {
            this.sellDate = sellDate;
        }
    }
}