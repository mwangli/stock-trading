package online.mwang.foundtrading.job;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.DailyPrice;
import online.mwang.foundtrading.bean.FoundDayRecord;
import online.mwang.foundtrading.bean.FoundTradingRecord;
import online.mwang.foundtrading.bean.StockInfo;
import online.mwang.foundtrading.mapper.FoundDayMapper;
import online.mwang.foundtrading.mapper.FoundTradingMapper;
import online.mwang.foundtrading.mapper.StockInfoMapper;
import online.mwang.foundtrading.utils.RequestUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 10:32
 * @description: DailyJob
 */
@Slf4j
@Component
@RequiredArgsConstructor

public class DailyJob {

    private static long count = 0L;
    private final RequestUtils requestUtils;
    private final StockInfoMapper StockInfoMapper;
    private final FoundTradingMapper foundTradingMapper;
    private final FoundDayMapper foundDayMapper;
    private final StringRedisTemplate redisTemplate;

    // 更新每日价格数据
    @Scheduled(cron = "9 9 9 * * *")
//    @Scheduled(fixedRate = 1000 * 60 * 60 * 24)
    public void updateDate() {
        for (int i = 1; i <= 20; i++) {
            listPage500(i);
        }
    }

    // 选出待出售股票
    @Scheduled(cron = "9 9 9 * * *")
//    @Scheduled(fixedRate = 1000 * 60 * 60 * 24)
    public void getSold() {
        String token = redisTemplate.opsForValue().get("requestToken");
        if (token == null) return;
        long timeMillis = System.currentTimeMillis();
        String param = "action=117&StartPos=0&MaxCount=20&reqno=" + timeMillis
                + "&intacttoserver=%40ClZvbHVtZUluZm8JAAAAN0EwOS1DMjdC&token=" + token + "&MobileCode=13278828091&newindex=1&cfrom=H5&tfrom=PC&CHANNEL=";
        JSONArray dataList = requestUtils.request2(param);
        for (int i = 1; i < dataList.size(); i++) {
            String data = dataList.getString(i);
            String[] split = data.split("\\|");
            String name = split[0];
            double number = Double.parseDouble(split[1]);
            if (number <= 0) continue;
            String code = split[9];
            Double price = Double.parseDouble(split[4]);
            Double amount = Double.parseDouble(split[8]);
            // 查询买入时间
            FoundTradingRecord selectRecord = foundTradingMapper.selectOne(new QueryWrapper<FoundTradingRecord>().eq("code", code).eq("sold", "0"));
            if (selectRecord == null) {
                // 写入交易数据
                FoundTradingRecord record = new FoundTradingRecord();
                record.setCode(code);
                record.setName(name);
                record.setBuyPrice(price);
                record.setBuyNumber(number);
                record.setBuyAmount(amount);
                Date now = new Date();
                record.setBuyDate(now);
                record.setSold("0");
                record.setCreateTime(now);
                record.setUpdateTime(now);
                foundTradingMapper.insert(record);
            } else {
                // 更新每日数据
                FoundDayRecord dayRecord = new FoundDayRecord();
                dayRecord.setCode(code);
                dayRecord.setName(name);
                SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
                dayRecord.setBuyDate(format.format(selectRecord.getBuyDate()));
                dayRecord.setBuyNumber(selectRecord.getBuyNumber());
                dayRecord.setBuyPrice(selectRecord.getBuyPrice());
                dayRecord.setBuyAmount(selectRecord.getBuyAmount());
                Date now = new Date();
                dayRecord.setTodayDate(format.format(now));
                dayRecord.setTodayPrice(price);
                double todayAmount = price * selectRecord.getBuyNumber();
                dayRecord.setTodayAmount(todayAmount);
                double income = todayAmount - selectRecord.getBuyAmount();
                dayRecord.setExpectedIncome(income);
                dayRecord.setCreateTime(now);
                dayRecord.setUpdateTime(now);
                int dateDiff = diffDate(selectRecord.getBuyDate(), now);
                double incomeRate = dateDiff == 0 ? 0 : income / dateDiff;
                dayRecord.setDailyIncomeRate(incomeRate);
                try {
                    foundDayMapper.insert(dayRecord);
                } catch (DuplicateKeyException e) {
                    log.warn("数据已存在！");
                }
            }
        }
    }

    public void listPage500(int page) {
        // 请求数据
        String pram = "c.funcno=21000&c.version=1&c.sort=1&c.order=0&c.type=0:2:9:18&c.curPage=" + page
                + "&c.rowOfPage=500&c.field=1:2:22:23:24:3:8:16:21:31&c.cfrom=H5&c.tfrom=PC&c.CHANNEL=";
        final JSONArray results = requestUtils.request(pram);
//        log.info(JSON.toJSONString(results));
        final ArrayList<StockInfo> stockInfos = new ArrayList<>();
        // 解析数据
        for (int i = 0; i < results.size(); i++) {
            final String s = results.getString(i);
            final String[] split = s.split(",");
            final String increase = split[0].replaceAll("\\[", "");
            final double increasePercent = Double.parseDouble(increase) * 100;
            final Double price = Double.parseDouble(split[1]);
            final String name = split[2].replaceAll("\"", "");
            final String market = split[3].replaceAll("\"", "");
            final String code = split[4].replaceAll("\"", "");
            final Date now = new Date();
            final StockInfo stockInfo = new StockInfo();
            stockInfo.setName(name);
            stockInfo.setCode(code);
            stockInfo.setMarket(market);
            stockInfo.setIncrease(increasePercent);
            stockInfo.setPrice(price);
            stockInfo.setCreateTime(now);
            stockInfo.setUpdateTime(now);
            stockInfos.add(stockInfo);
        }
        // 保存数据
        for (StockInfo info : stockInfos) {
            try {
                StockInfoMapper.insert(info);
                log.info("保存第{}条数据：{}", count++, info);
            } catch (DuplicateKeyException e) {
                log.warn("数据已存在：{}", info);
            }
            // 更新历史价格
            updateDailyPrice(info.getCode(), info.getMarket(), info.getPrice());
        }
    }

    public void updateDailyPrice(String code, String market, Double price) {
        // 获取历史数据
        String param = "c.funcno=20009&c.version=1&c.stock_code=" +
                code + "&c.market=" + market + "&c.type=day&c.count=20&c.cfrom=H5&c.tfrom=PC&c.CHANNEL=";
        final JSONArray results = requestUtils.request(param);
        // 解析数据
        final ArrayList<DailyPrice> prices = new ArrayList<>();
        if (results.size() >= 10) {
            for (int i = results.size() - 10; i < results.size(); i++) {
                final String s = results.getString(i);
                final String[] split = s.split(",");
                final String date = split[0].replaceAll("\\[", "");
                final String price1 = split[1];
                final double v = Double.parseDouble(price1);
                final DailyPrice dailyPrice = new DailyPrice(date, v / 100);
                prices.add(dailyPrice);
            }
            // 更新历史价格
            final StockInfo stockInfo = new StockInfo();
            stockInfo.setCode(code);
            stockInfo.setPrices(JSONObject.toJSONString(prices));
            stockInfo.setUpdateTime(new Date());
            // 计算日增长率曲线
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
            DailyPrice dailyPrice = new DailyPrice(format.format(new Date()), price);
            prices.add(dailyPrice);
            final List<Double> rateList = getRateList(prices);
            stockInfo.setIncreaseRate(JSONObject.toJSONString(rateList));
            // 计算得分
            final Double score = getScore(rateList);
            stockInfo.setScore(score);
            StockInfoMapper.update(stockInfo, new QueryWrapper<StockInfo>().eq("code", code));
            log.info("更新历史价格：{} {} {}", market, code, score);
        } else {
            log.warn("历史价格少于11条，丢弃该数据");
        }
    }

    public List<Double> getRateList(List<DailyPrice> prices) {
        final ArrayList<Double> rateList = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            final Double price = prices.get(i).getPrice();
            final Double oldPrice = prices.get(i - 1).getPrice();
            final double dailyIncreaseRate = (price - oldPrice) / oldPrice;
            // 增长率乘100方便计算，否则数值太小
            rateList.add(Double.parseDouble(String.format("%.4f", dailyIncreaseRate * 100)));
        }
        return rateList;
    }

    // 获取最近10个交易日天的日增长率，用以计算增长的稳定性和增长率总和
    // 稳定性用方差来衡量,增长率总和体现增长幅度
    // 增长的天数越多，日增长率总和越大，方差越小，增长波动越小，代表稳定增长，评分越高
    public Double getScore(List<Double> rateList) {
        // 计算和与平均值
        final double fixSum = rateList.stream().reduce(Double::sum).orElse(0.0);
        final double avg = fixSum / rateList.size();
        // 计算方差
        double sum = 0;
        for (Double price : rateList) {
            sum += Math.pow((price - avg), 2);
        }
        double sqrt = Math.sqrt(sum / rateList.size());
        sqrt = sqrt > 10 ? 10 : sqrt;
        final double score = fixSum * (1 - (sqrt / 10));
        log.info("数据：{}， 日增长率总和：{}, 平均值：{}，方差：{}，得分：{}", rateList, fixSum, avg, sqrt, score);
        return score;
    }

    public int diffDate(Date date1, Date date2) {
        long spent = date2.getTime() - date1.getTime();
        return (int) Math.ceil(spent * 1.0 / (1000 * 60 * 60 * 24));
    }

}
