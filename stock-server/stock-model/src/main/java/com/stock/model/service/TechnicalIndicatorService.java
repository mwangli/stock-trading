// AI_GENERATE_START------
package com.stock.model.service;

import com.stock.data.dto.StockPriceItemDto;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 技术指标计算服务。
 * 将按交易日排序的行情序列转换为 LSTM 特征所需的 RSI、MACD、SMA、布林带和 OBV 指标。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
public class TechnicalIndicatorService {

    /**
     * 计算模型预处理所需的技术指标序列。
     *
     * @param prices 按交易日期升序排列的股票日线数据
     * @return 以指标名称为键、与行情序列等长的数值数组
     */
    public Map<String, double[]> calculateIndicators(List<StockPriceItemDto> prices) {
        BarSeries series = new BaseBarSeries("stock");
        for (StockPriceItemDto price : prices) {
            ZonedDateTime zdt = ZonedDateTime.of(price.getDate().atStartOfDay(), ZoneId.systemDefault());
            series.addBar(zdt, price.getOpenPrice(), price.getHighPrice(), price.getLowPrice(), price.getClosePrice(), price.getVolume());
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // RSI
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        // MACD
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);

        // SMA
        SMAIndicator sma = new SMAIndicator(closePrice, 20);

        // Bollinger Bands
        BollingerBandsMiddleIndicator middleBBand = new BollingerBandsMiddleIndicator(sma);
        StandardDeviationIndicator stdDev = new StandardDeviationIndicator(closePrice, 20);
        BollingerBandsUpperIndicator upperBBand = new BollingerBandsUpperIndicator(middleBBand, stdDev);
        BollingerBandsLowerIndicator lowerBBand = new BollingerBandsLowerIndicator(middleBBand, stdDev);

        // On-Balance Volume
        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(series);

        int size = series.getBarCount();
        double[] rsiValues = new double[size];
        double[] macdValues = new double[size];
        double[] smaValues = new double[size];
        double[] upperBBandValues = new double[size];
        double[] lowerBBandValues = new double[size];
        double[] obvValues = new double[size];

        for (int i = 0; i < size; i++) {
            rsiValues[i] = rsi.getValue(i).doubleValue();
            macdValues[i] = macd.getValue(i).doubleValue();
            smaValues[i] = sma.getValue(i).doubleValue();
            upperBBandValues[i] = upperBBand.getValue(i).doubleValue();
            lowerBBandValues[i] = lowerBBand.getValue(i).doubleValue();
            obvValues[i] = obv.getValue(i).doubleValue();
        }

        Map<String, double[]> indicators = new HashMap<>();
        indicators.put("RSI", rsiValues);
        indicators.put("MACD", macdValues);
        indicators.put("SMA", smaValues);
        indicators.put("UpperBoll", upperBBandValues);
        indicators.put("LowerBoll", lowerBBandValues);
        indicators.put("OBV", obvValues);

        return indicators;
    }
}

// AI_GENERATE_END------
