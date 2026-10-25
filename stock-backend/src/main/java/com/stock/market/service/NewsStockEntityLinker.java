// AI_GENERATE_START --
package com.stock.market.service;

import com.stock.market.entity.StockInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新闻文本股票实体链接器。
 * 优先识别六位证券代码，再按较长股票名称匹配，并允许来源限制正文观察窗口。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
public class NewsStockEntityLinker {

    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("(?<!\\d)([03648]\\d{5})(?!\\d)");

    /**
     * 在受限文本窗口中识别关联股票。
     *
     * @param text 标题与正文组合文本
     * @param stocks 当前股票列表
     * @param maximumTextLength 最大识别文本长度，防止长监管文书把开户行等服务机构误识别为处罚对象
     * @param maximumMatches 单篇文档最多关联股票数
     * @return 去重后的关联股票列表
     */
    public List<StockInfo> link(String text, List<StockInfo> stocks,
                                int maximumTextLength, int maximumMatches) {
        if (text == null || text.isBlank() || stocks == null || stocks.isEmpty() || maximumMatches <= 0) {
            return List.of();
        }
        String evidence = text.length() <= maximumTextLength ? text : text.substring(0, maximumTextLength);
        Map<String, StockInfo> byCode = new LinkedHashMap<>();
        for (StockInfo stock : stocks) {
            if (stock != null && stock.getCode() != null && stock.getCode().matches("\\d{6}")) {
                byCode.put(stock.getCode(), stock);
            }
        }
        Map<String, StockInfo> matches = new LinkedHashMap<>();
        Matcher codeMatcher = STOCK_CODE_PATTERN.matcher(evidence);
        while (codeMatcher.find() && matches.size() < maximumMatches) {
            StockInfo stock = byCode.get(codeMatcher.group(1));
            if (stock != null) {
                matches.put(stock.getCode(), stock);
            }
        }
        if (matches.size() >= maximumMatches) {
            return new ArrayList<>(matches.values());
        }
        stocks.stream()
                .filter(stock -> stock != null && stock.getCode() != null && stock.getName() != null)
                .filter(stock -> stock.getName().length() >= 3)
                .sorted(Comparator.comparingInt((StockInfo stock) -> stock.getName().length()).reversed())
                .filter(stock -> evidence.contains(stock.getName()))
                .forEach(stock -> {
                    if (matches.size() < maximumMatches) {
                        matches.put(stock.getCode(), stock);
                    }
                });
        return new ArrayList<>(matches.values());
    }
}
// AI_GENERATE_END --
