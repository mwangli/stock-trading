package online.mwang.foundtrading.job;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.DailyPrice;
import online.mwang.foundtrading.bean.FoundTradingRecord;
import online.mwang.foundtrading.bean.StockInfo;
import online.mwang.foundtrading.mapper.FoundDayMapper;
import online.mwang.foundtrading.mapper.FoundTradingMapper;
import online.mwang.foundtrading.mapper.StockInfoMapper;
import online.mwang.foundtrading.utils.RequestUtils;
import online.mwang.foundtrading.utils.SleepUtils;
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

    private static long BUY_RETRY_TIMES = 3;
    private final RequestUtils requestUtils;
    private final StockInfoMapper StockInfoMapper;
    private final FoundTradingMapper foundTradingMapper;
    private final FoundDayMapper foundDayMapper;
    private final StringRedisTemplate redisTemplate;

    // 每日任务处理
//    @Scheduled(cron = "0 10 9 * * *")
    @Scheduled(fixedRate = 1000 * 60 * 60 * 24)
    public void runJob() {
        log.info("执行每日任务...");
        // 卖出旧股
        sold(1);
        // 买入新股
        buy();
    }

    // 每隔半小时更新Token
    @Scheduled(fixedRate = 1000 * 60 * 25)
    public void refreshToken() {
        cancelOrder("");
    }

    public void sold(int times) {
        if (times > BUY_RETRY_TIMES) {
            log.error("卖出股票失败，请检查程序代码！");
            return;
        }
        log.info("第{}次尝试卖出股票---------", times);
        String token = redisTemplate.opsForValue().get("requestToken");
        long timeMillis = System.currentTimeMillis();
        String param = "action=117&StartPos=0&MaxCount=20&reqno=" + timeMillis
                + "&intacttoserver=%40ClZvbHVtZUluZm8JAAAAN0EwOS1DMjdC&token=" + token
                + "&MobileCode=13278828091&newindex=1&cfrom=H5&tfrom=PC&CHANNEL=";
        JSONArray dataList = requestUtils.request2(param);
        double maxRate = -100.00;
        FoundTradingRecord maxRateRecord = null;
        for (int i = 1; i < dataList.size(); i++) {
            String data = dataList.getString(i);
            String[] split = data.split("\\|");
            String name = split[0];
//            String accountType = split[14];
            double number = Double.parseDouble(split[1]);
            if (number <= 0) {
                continue;
            }
            Double price = Double.parseDouble(split[4]);
            String code = split[9];
            // 查询买入时间
            FoundTradingRecord selectRecord = foundTradingMapper.selectOne(new QueryWrapper<FoundTradingRecord>().eq("code", code).eq("sold", "0"));
            if (selectRecord == null) {
                log.info("未查询到买入记录，新增交易记录");
                // 写入交易数据
                FoundTradingRecord record = new FoundTradingRecord();
                record.setCode(code);
                record.setName(name);
//                record.setAccountType(accountType);
                record.setBuyPrice(price);
                record.setBuyNumber(number);
                record.setBuyAmount(price * number + 5);
                Date now = new Date();
                record.setBuyDate(now);
                record.setSold("0");
                record.setCreateTime(now);
                record.setUpdateTime(now);
                foundTradingMapper.insert(record);
            } else {
                // 更新每日数据
                double saleAmount = price * selectRecord.getBuyNumber() + 5;
                double income = saleAmount - selectRecord.getBuyAmount();
                int dateDiff = diffDate(selectRecord.getBuyDate(), new Date());
                double incomeRate = dateDiff == 0 ? 0 : income / selectRecord.getBuyAmount() / dateDiff * 100;
                if (incomeRate > maxRate) {
                    selectRecord.setSalePrice(price);
                    selectRecord.setSaleNumber(selectRecord.getBuyAmount());
                    selectRecord.setSaleAmount(saleAmount);
                    selectRecord.setRealIncome(income);
                    selectRecord.setRealIncomeRate(incomeRate);
                    maxRateRecord = selectRecord;
                }
            }
        }
        // 卖出最高收益的股票
        if (maxRateRecord == null) {
            log.warn("无法获取最高收益股票，无法进行卖出交易");
        } else {
            log.info("最佳卖出股票{}-{}，买入金额{}，卖出金额{}，收益{}，收益率{}", maxRateRecord.getCode(), maxRateRecord.getName(), maxRateRecord.getBuyAmount(),
                    maxRateRecord.getSaleAmount(), maxRateRecord.getRealIncome(), String.format("%.4f", maxRateRecord.getRealIncomeRate()));
            // 返回合同编号
            final String saleNo = buySale("S", maxRateRecord.getCode(), maxRateRecord.getSalePrice(), maxRateRecord.getBuyNumber());
            if (saleNo == null) {
                log.info("第{}次尝试卖出失败---------", times++);
                sold(times);
            } else {
                // 等待一分钟后查询卖出结果
                log.info("等待一分钟后查询卖出交易结果...");
                SleepUtils.second(60);
                if (queryStatus(saleNo)) {
                    maxRateRecord.setSaleDate(new Date());
                    maxRateRecord.setSold("1");
                    foundTradingMapper.updateById(maxRateRecord);
                    log.info("成功卖出股票{}-{}, 卖出金额为{}, 收益为{}，日收益率为{}", maxRateRecord.getCode(), maxRateRecord.getName(),
                            maxRateRecord.getSaleAmount(), maxRateRecord.getRealIncome(), maxRateRecord.getRealIncomeRate());
                } else {
                    // 如果交易不成功，撤单后重新计算卖出
                    log.info("卖出交易不成功，进行撤单操作");
                    cancelOrder(saleNo);
                    log.info("第{}次尝试卖出失败---------", times++);
                    sold(times);
                }
            }
        }
    }

    public void cancelOrder(String answerNo) {
        String token = redisTemplate.opsForValue().get("requestToken");
        final long timeMillis = System.currentTimeMillis();
        String param = "action=111&ContactID=" + answerNo + "&op_station=4%7C+%7C+%7C+%7C+%7C+%7C+%7C%7C+&reqno=" + timeMillis
                + "&intacttoserver=%40ClZvbHVtZUluZm8JAAAAN0EwOS1DMjdC&token=" + token + "&MobileCode=13278828091&newindex=1&cfrom=H5&tfrom=PC&CHANNEL=";
        final JSONObject result = requestUtils.request3(param);
        final String newToken = result.getString("TOKEN");
        redisTemplate.opsForValue().set("requestToken", newToken);
    }

    public String buySale(String type, String code, Double price, Double number) {
        // 请求数据
        String token = redisTemplate.opsForValue().get("requestToken");
        final long timeMillis = System.currentTimeMillis();
        String param = "action=110&PriceType=0&Direction=" + type + "&StockCode=" + code
                + "&Price=" + price + "&Volume=" + number + "&WTAccount=&WTAccountType=&ProPrice=&op_station=4%7C+%7C+%7C+%7C+%7C+%7C+%7C%7C+&reqno="
                + timeMillis + "&intacttoserver=%40ClZvbHVtZUluZm8JAAAAN0EwOS1DMjdC&token=" + token + "&MobileCode=13278828091&newindex=1&cfrom=H5&tfrom=PC&CHANNEL=";
        final JSONObject result = requestUtils.request3(param);
        final String newToken = result.getString("TOKEN");
        redisTemplate.opsForValue().set("requestToken", newToken);
        return result.getString("ANSWERNO");
    }

    public boolean queryStatus(String answerNo) {
        // 请求数据
        final long timeMillis = System.currentTimeMillis();
        final String token = redisTemplate.opsForValue().get("requestToken");
        String pram = "MobileCode=13278828091&&CHANNEL=&Token=" + token + "&Reqno=" + timeMillis
                + "&ReqlinkType=1&newindex=1&action=113&StartPos=0&MaxCount=20&intacttoserver=@ClZvbHVtZUluZm8JAAAAN0EwOS1DMjdC&cfrom=H5&tfrom=PC";
        final JSONArray results = requestUtils.request2(pram);
        // 解析数据
        boolean res = false;
        for (int i = 1; i < results.size(); i++) {
            final String result = results.getString(i);
            final String[] split = result.split("\\|");
            final String status = split[2];
            final String answerNo1 = split[8];
            if (answerNo1.equals(answerNo) && "已成".equals(status)) {
                res = true;
                break;
            }
        }
        return res;
    }

    public void buy() {
        //  更新每日数据
        for (int i = 1; i <= 20; i++) {
            listPage500(i);
        }
        log.info("每日数据更新完成！");
        // 选择得分最高的一组买入
        final List<StockInfo> bestList = StockInfoMapper.slectBest();
        for (int i = 0; i < bestList.size(); i++) {
            StockInfo best = bestList.get(i);
            log.info("尝试买入第{}支股票{}-{},评分{}日增长率{}", i + 1, best.getCode(), best.getName(), best.getScore(), best.getIncreaseRate());
//            String accountType = "SH".equals(best.getMarket()) ? "SHACCOUNT" : "SZACCOUNT";
            double buyNumber = 100.00;
            final String buyNo = buySale("B", best.getCode(), best.getPrice(), buyNumber);
            if (buyNo == null) {
                log.info("无法买入当前股票，尝试买入下一股票");
            } else {
                // 等待10秒后后查询买入结果
                log.info("等待10秒后查询买入结果...");
                SleepUtils.second(10);
                if (queryStatus(buyNo)) {
                    final FoundTradingRecord record = new FoundTradingRecord();
                    record.setCode(best.getCode());
                    record.setName(best.getName());
//                record.setAccountType(accountType);
                    record.setBuyNumber(buyNumber);
                    record.setBuyAmount(best.getPrice() * buyNumber + 5);
                    final Date now = new Date();
                    record.setBuyDate(now);
                    record.setSold("0");
                    record.setCreateTime(now);
                    record.setUpdateTime(now);
                    foundTradingMapper.insert(record);
                    log.info("成功买入股票{}-{}, 买入价格{}，买入数量{}，买入金额{}", record.getCode(), record.getName(),
                            record.getBuyPrice(), record.getBuyNumber(), record.getBuyAmount());
                    break;
                } else {
                    // 如果交易不成功，撤单后重新计算卖出
                    log.info("当前买入交易不成功，进行撤单操作");
                    cancelOrder(buyNo);
//                    i--;
                }
            }
            if (i + 1 == bestList.size()) {
                log.error("买入股票失败，请检查程序！");
            }
        }
    }

    public void listPage500(int page) {
        // 请求数据
        String pram = "c.funcno=21000&c.version=1&c.sort=1&c.order=0&c.type=0:2:9:18&c.curPage=" + page
                + "&c.rowOfPage=500&c.field=1:2:22:23:24:3:8:16:21:31&c.cfrom=H5&c.tfrom=PC&c.CHANNEL=";
        final JSONArray results = requestUtils.request(pram);
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
        final List<String> codes = StockInfoMapper.selectCodes();
        for (StockInfo info : stockInfos) {
            if (!codes.contains(info.getCode())) {
                StockInfoMapper.insert(info);
                log.info("保存数据：{}", info);
            }
            // 更新历史价格
            updateDailyPrice(info.getCode(), info.getMarket(), info.getPrice());
        }
    }


    public void updateDailyPrice(String code, String market, Double price) {
        final StockInfo selectedInfo = StockInfoMapper.selectOne(new QueryWrapper<StockInfo>().eq("code", code));
        final List<DailyPrice> prices;
        if (selectedInfo.getPrices() == null) {
            prices = getPrices(code, market);
            updatePrices(code, prices);
        } else {
            final String selectedInfoPrices = selectedInfo.getPrices();
            prices = JSONObject.parseArray(selectedInfoPrices, DailyPrice.class);
            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            final String date = dateFormat.format(new Date());
            if (prices.stream().noneMatch(p -> p.getDate().equals(date))) {
                final DailyPrice dailyPrice = new DailyPrice(date, price);
                prices.add(dailyPrice);
                if (prices.size() > 11) {
                    prices.remove(0);
                }
                // 更新历史价格
                updatePrices(code, prices);
            }
        }
    }

    public List<DailyPrice> getPrices(String code, String market) {
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
        } else {
            log.warn("历史数据不足10条，丢弃该数据。");
        }
        return prices;
    }

    public void updatePrices(String code, List<DailyPrice> prices) {
        // 更新历史价格
        final StockInfo stockInfo = new StockInfo();
        stockInfo.setCode(code);
        stockInfo.setPrices(JSONObject.toJSONString(prices));
        stockInfo.setUpdateTime(new Date());
        // 计算日增长率曲线
        final List<Double> rateList = getRateList(prices);
        stockInfo.setIncreaseRate(JSONObject.toJSONString(rateList));
        // 计算得分
        final Double score = getScore(rateList);
        stockInfo.setScore(score);
        StockInfoMapper.update(stockInfo, new QueryWrapper<StockInfo>().eq("code", code));
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
        if (rateList.isEmpty()) {
            return 0.0;
        }
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
        log.info("增长率总和:{},方差:{},得分:{}", String.format("%.4f", fixSum), String.format("%.4f", sqrt), String.format("%.4f", score));
        return score;
    }

    public int diffDate(Date date1, Date date2) {
        long spent = date2.getTime() - date1.getTime();
        return (int) Math.ceil(spent * 1.0 / (1000 * 60 * 60 * 24));
    }

}
