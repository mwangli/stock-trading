package online.mwang.foundtrading.job;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.po.*;
import online.mwang.foundtrading.mapper.AccountInfoMapper;
import online.mwang.foundtrading.mapper.ScoreStrategyMapper;
import online.mwang.foundtrading.mapper.StockInfoMapper;
import online.mwang.foundtrading.service.StockInfoService;
import online.mwang.foundtrading.service.TradingRecordService;
import online.mwang.foundtrading.utils.DateUtils;
import online.mwang.foundtrading.utils.RequestUtils;
import online.mwang.foundtrading.utils.SleepUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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

    // 最大持股数量，限制可买入股票的最大价格
    private static final int MAX_HOLD_NUMBER = 100;
    private static final int MIN_HOLD_NUMBER = 100;
    // 最大持仓股票数量
    private static final int MAX_HOLD_STOCKS = 5;
    // 最低价格系数，保证可买入股票价格不会过低
    private static final double LOW_PRICE_PERCENT = 0.8;
    // 最低价格限制，资金不足时不买入低价股
    private static final double LOW_PRICE_LIMIT = 5.0;
    private static final int BUY_RETRY_TIMES = 3;
    private static final int SOLD_RETRY_TIMES = 3;
    private static final int PRICE_UP_LIMIT = 3;
    private static final int PRICE_FALL_LIMIT = 3;
    private static final int BUY_RETRY_LIMIT = 10;
    private static final int WAIT_TIME_SECONDS = 10;
    private static final int HISTORY_PRICE_LIMIT = 100;
    private static final int UPDATE_BATCH_SIZE = 500;
    private static final int THREAD_POOL_NUMBERS = 10;
    private static final String BUY_TYPE_OP = "B";
    private static final String SALE_TYPE_OP = "S";
    private static HashMap<String, Integer> dateMap;
    private final RequestUtils requestUtils;
    private final StockInfoService stockInfoService;
    private final TradingRecordService tradingRecordService;
    private final AccountInfoMapper accountInfoMapper;
    private final StringRedisTemplate redisTemplate;
    private final StockInfoMapper stockInfoMapper;
    private final ScoreStrategyMapper strategyMapper;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_NUMBERS);

    // 每隔25分钟刷新Token
//    @Scheduled(fixedRate = 1000 * 60 * 25, initialDelay = 1000 * 60 * 5)
    public void refreshToken() {
        buySale(SALE_TYPE_OP, "", 0.0, 0.0);
        log.info("刷新Token任务执行完毕。");
    }

    // 交易日开盘时间买入 9:30
//    @Scheduled(cron = "0 0,15,30 9 ? * MON-FRI")
    public void runBuyJob() {
        log.info("开始执行买入任务====================================");
        refreshToken();
        buy(0);
        log.info("买入任务执行完毕====================================");
    }

    // 交易日收盘时间卖出 14:30
//    @Scheduled(cron = "0 0 10,11,14 ? * MON-FRI")
    public void runSaleJob() {
        log.info("开始执行卖出任务====================================");
        refreshToken();
        sale(0);
        log.info("卖出任务执行完毕====================================");
    }

    // 更新账户余额，交易时间段内每小时执行一次
//    @Scheduled(cron = "0 0 9-15 ? * MON-FRI")
    public void runAccountJob() {
        log.info("更新账户余额任务执行开始====================================");
        refreshToken();
        updateAccountAmount();
        log.info("更新账户余额任务执行结束====================================");
    }


    // 更新股票实时价格，交易日每天上午八点执行
//    @Scheduled(cron = "0 0 8 ? * MON-FRI")
    public void runNowJob() {
        log.info("更新股票实时价格任务执行开始====================================");
        refreshToken();
        updateNowPrice();
        log.info("更新股票实时价格任务执行结束====================================");
    }

    // 更新股票历史价格，交易日每天下午三点执行
//    @Scheduled(cron = "0 0 15 ? * MON-FRI")
    public void runHistoryJob() {
        log.info("更新股票历史价格任务执行开始====================================");
        refreshToken();
        updateHistoryPrice();
        log.info("更新股票历史价格任务执行结束====================================");
    }

    // 同步股票交易记录，每十天执行一次
//    @Scheduled(cron = "0 0 15 1/10 * ?")
    public void runSyncJob() {
        log.info("同步订单任务执行开始====================================");
        refreshToken();
        syncBuySaleRecord();
        syncBuySaleCount();
        log.info("同步订单执行结束====================================");
    }

    // 刷新股票交易权限，每半月月执行一次
//    @Scheduled(cron = "0 0 12 1,15 * ?")
    public void runFlushJob() {
        log.info("更新权限任务执行开始====================================");
        refreshToken();
        flushPermission();
        log.info("更新权限任务执行结束====================================");
    }

    public String buildParams(Map<String, Object> paramMap) {
        paramMap.put("cfrom", "H5");
        paramMap.put("tfrom", "PC");
        paramMap.put("newindex", "1");
        paramMap.put("MobileCode", "13278828091");
        paramMap.put("intacttoserver", "%40ClZvbHVtZUluZm8JAAAAN0EwOS1DMjdC");
        StringBuilder stringBuilder = new StringBuilder();
        paramMap.forEach((k, v) -> stringBuilder.append(k).append("=").append(v).append("&"));
        return stringBuilder.toString();
    }

    @SneakyThrows
    public void flushPermission() {
        // 此处不能使用多线程处理，因为每次请求会使上一个Token失效
        stockInfoMapper.resetPermission();
        List<StockInfo> stockInfos = stockInfoService.list();
        stockInfos.forEach(info -> {
            JSONObject res = buySale(BUY_TYPE_OP, info.getCode(), 1000.0, 100.0);
            final String errorNo = res.getString("ERRORNO");
            if (!errorNo.equals("-57")) {
                info.setPermission("0");
            }
        });
        saveDate(stockInfos);
        // 取消所有提交的订单
        cancelAllOrder(10);
    }

    public void sale(int times) {
        if (times >= SOLD_RETRY_TIMES) {
            log.error("{}次尝试卖出股票失败，请检查程序代码！", times);
            return;
        }
        log.info("第{}次尝试卖出股票---------", times + 1);
        // 检查今天是否有卖出记录，防止重复卖出
        LambdaQueryWrapper<TradingRecord> queryWrapper = new QueryWrapper<TradingRecord>().lambda().eq(TradingRecord::getSold, "1")
                .eq(TradingRecord::getSaleDateString, DateUtils.dateFormat.format(new Date()));
        List<TradingRecord> hasSold = tradingRecordService.list(queryWrapper);
        if (!CollectionUtils.isEmpty(hasSold)) {
            log.warn("今天已经有卖出记录了，无需重复卖出!");
            return;
        }
        String token = redisTemplate.opsForValue().get("requestToken");
        long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", "117");
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", 20);
        paramMap.put("reqno", timeMillis);
        paramMap.put("token", token);
        paramMap.put("Volume", 100);
        JSONArray dataList = requestUtils.request2(buildParams(paramMap));
        double maxDailyRate = -100.00;
        TradingRecord maxRateRecord = null;
        for (int i = 1; i < dataList.size(); i++) {
            String data = dataList.getString(i);
            String[] split = data.split("\\|");
            String code = split[9];
            String name = split[0];
            // 持仓数量，持仓数量为0代表该股票今日已卖出
            double hasNumber = Double.parseDouble(split[1]);
            if (hasNumber == 0) {
                log.info("当前股票[{}-{}]持有数量为0，已经卖出无需处理", code, name);
                continue;
            }
            // 可用数量，已委托订单或者不在交易时间段内会导致可用数量为0
            double availableNumber = Double.parseDouble(split[2]);
            double price = Double.parseDouble(split[4]);
            // 查询买入时间
            TradingRecord selectRecord = tradingRecordService.getOne(new QueryWrapper<TradingRecord>().eq("code", code).eq("sold", "0"));
            if (selectRecord == null) {
                log.info("当前股票[{}-{}]未查询到买入记录,开始同步最近一个月订单", code, name);
                threadPool.submit(this::syncBuySaleRecord);
            } else {
                if (availableNumber == 0) {
                    log.info("当前股票[{}-{}]可卖出数量为0，请检查订单或者非交易时间段！", code, name);
                } else {
                    // 更新每日数据
                    final double amount = price * availableNumber;
                    double saleAmount = amount - getPeeAmount(amount);
                    double income = saleAmount - selectRecord.getBuyAmount();
                    int dateDiff = diffDate(selectRecord.getBuyDate(), new Date());
                    double incomeRate = income / selectRecord.getBuyAmount() * 100;
                    double dailyIncomeRate = incomeRate / dateDiff;
                    log.info("当前股票[{}-{}]，买入金额:{}，卖出金额:{}，收益:{}元，日收益率:{}%", selectRecord.getCode(), selectRecord.getName(), selectRecord.getBuyAmount(), saleAmount, income, dailyIncomeRate);
                    if (dailyIncomeRate > maxDailyRate) {
                        selectRecord.setSalePrice(price);
                        selectRecord.setSaleNumber(selectRecord.getBuyAmount());
                        selectRecord.setSaleAmount(saleAmount);
                        selectRecord.setIncome(income);
                        selectRecord.setIncomeRate(incomeRate);
                        selectRecord.setHoldDays(dateDiff);
                        selectRecord.setDailyIncomeRate(dailyIncomeRate);
                        maxDailyRate = dailyIncomeRate;
                        maxRateRecord = selectRecord;
                    }
                }
            }
        }
        // 卖出最高收益的股票
        if (maxRateRecord == null) {
            log.error("无可卖出股票，无法进行卖出交易！");
        } else {
            log.info("最佳卖出股票[{}-{}]，买入金额:{}，卖出金额:{}，收益:{}，日收益率:{}", maxRateRecord.getCode(), maxRateRecord.getName(), maxRateRecord.getBuyAmount(), maxRateRecord.getSaleAmount(), maxRateRecord.getIncome(), String.format("%.4f", maxRateRecord.getDailyIncomeRate()));
            // 等待最佳卖出时机
//            int priceFallCount = 0;
//            int timesCount = 0;
//            while (timesCount < 36) {
//                SleepUtils.second(5);
//                final Double nowPrice = maxRateRecord.getSalePrice();
//                final Double lastPrice = getLastPrice(maxRateRecord.getCode());
//                maxRateRecord.setSalePrice(lastPrice);
//                if (lastPrice > nowPrice) {
//                    priceFallCount = 0;
//                    timesCount = 0;
//                    log.info("最佳卖出股票[{}-{}]，当前价格：{}，等待最佳卖出时机", maxRateRecord.getCode(), maxRateRecord.getName(), lastPrice);
//                } else if (lastPrice < nowPrice) {
//                    priceFallCount++;
//                    timesCount = 0;
//                    if (priceFallCount >= PRICE_FALL_LIMIT) {
//                        log.info("开始卖出股票[{}-{}]，卖出价格：{}", maxRateRecord.getCode(), maxRateRecord.getName(), maxRateRecord.getSalePrice());
//                        break;
//                    }
//                } else {
//                    priceFallCount = 0;
//                    timesCount++;
//                    log.info("最佳卖出股票[{}-{}]，当前价格：{}，等待最佳卖出时机", maxRateRecord.getCode(), maxRateRecord.getName(), maxRateRecord.getSalePrice());
//                }
//            }
            // 返回合同编号
            JSONObject res = buySale(SALE_TYPE_OP, maxRateRecord.getCode(), maxRateRecord.getSalePrice(), maxRateRecord.getBuyNumber());
            String saleNo = res.getString("ANSWERNO");
            if (saleNo == null) {
                log.info("第{}次尝试卖出失败---------", times + 1);
                sale(times + 1);
            } else {
                // 等待一分钟后查询卖出结果
                log.info("等待10秒后查询卖出交易结果...");
                SleepUtils.second(10);
                if (queryStatus(saleNo)) {
                    maxRateRecord.setSold("1");
                    maxRateRecord.setSaleNo(saleNo);
                    final Date now = new Date();
                    maxRateRecord.setSaleDate(now);
                    maxRateRecord.setSaleDateString(DateUtils.dateFormat.format(now));
                    maxRateRecord.setUpdateTime(now);
                    tradingRecordService.updateById(maxRateRecord);
                    // 更新账户资金
                    updateAccountAmount();
                    // 增加股票交易次数
                    StockInfo stockInfo = stockInfoService.getOne(new QueryWrapper<StockInfo>().lambda().eq(StockInfo::getCode, maxRateRecord.getCode()));
                    stockInfo.setBuySaleCount(stockInfo.getBuySaleCount() + 1);
                    stockInfoService.updateById(stockInfo);
                    log.info("成功卖出股票[{}-{}], 卖出金额为:{}, 收益为:{}，日收益率为:{}", maxRateRecord.getCode(), maxRateRecord.getName(), maxRateRecord.getSaleAmount(), maxRateRecord.getIncome(), maxRateRecord.getDailyIncomeRate());
                    // 卖出成功后执行买入逻辑
                    buy(0);
                } else {
                    // 如果交易不成功，撤单后重新计算卖出
                    log.info("卖出交易不成功，进行撤单操作");
                    cancelOrder(saleNo);
                    SleepUtils.second(WAIT_TIME_SECONDS);
                    log.info("第{}次尝试卖出失败---------", times + 1);
                    sale(times + 1);
                }
            }
        }
    }

    // 获取持仓股票
    private Double getLastPrice(String code) {
        long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("stockcode", code);
        paramMap.put("Reqno", timeMillis);
        paramMap.put("action", 33);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("Level", 1);
        paramMap.put("UseBPrice", 1);
        JSONObject res = requestUtils.request3(buildParams(paramMap));
        return res.getDouble("PRICE");
    }

    public void buy(int times) {
        if (times >= BUY_RETRY_TIMES) {
            log.error("{}次尝试买入股票失败，请检查程序代码！", times);
            return;
        }
        log.info("第{}次尝试买入股票---------", times + 1);
        // 查询持仓股票数量
        final long holdCount = tradingRecordService.list(new LambdaQueryWrapper<TradingRecord>().eq(TradingRecord::getSold, "0")).stream().count();
        final long needCount = MAX_HOLD_STOCKS - holdCount;
        if (needCount <= 0) {
            log.info("持仓股票数量已达到最大值:{}，无需购买!", MAX_HOLD_STOCKS);
            return;
        }
        // 撤销所有未成功订单，回收可用资金
        cancelAllOrder(1);
        // 更新账户可用资金
        final AccountInfo accountInfo = updateAccountAmount();
        final Double totalAvailableAmount = accountInfo.getAvailableAmount();
        final Double totalAmount = accountInfo.getTotalAmount();
        final double maxAmount = totalAmount / MAX_HOLD_STOCKS;
        // 计算此次可用资金
        double availableAmount = totalAvailableAmount / needCount;
        availableAmount = Math.min(availableAmount, maxAmount);
        // 计算可买入股票价格区间
        final double highPrice = availableAmount / MIN_HOLD_NUMBER;
        final double lowPrice = (availableAmount / MAX_HOLD_NUMBER) * LOW_PRICE_PERCENT;
        log.info("当前可用资金{}元, 可买入价格区间[{},{}]", availableAmount, lowPrice, highPrice);
//        if (lowPrice < LOW_PRICE_LIMIT) {
//            log.info("可用资金资金不足，取消购买任务！");
//            return;
//        }
        //  获取实时价格
        final List<StockInfo> dataList = getDataList();
        // 计算得分
        List<StockInfo> stockInfos = calculateScore(dataList);
        // 选择有交易权限合适价格区间的数据，按评分排序分组
        final List<StockInfo> limitList = stockInfos.stream()
                .filter(s -> "1".equals(s.getPermission()) && s.getPrice() >= lowPrice && s.getPrice() <= highPrice)
                .sorted(Comparator.comparingDouble(StockInfo::getScore))
                .skip((long) times * BUY_RETRY_LIMIT).limit(BUY_RETRY_LIMIT).collect(Collectors.toList());
        // 在得分高的一组中随机选择一支买入
        List<String> buyCodes = tradingRecordService.list().stream().filter(s -> "0".equals(s.getSold())).map(TradingRecord::getCode).collect(Collectors.toList());
        StockInfo best = limitList.get(new Random().nextInt(BUY_RETRY_LIMIT));
        if (buyCodes.contains(best.getCode())) {
            log.info("当前股票[{}-{}]已经持有，尝试买入下一组股票", best.getCode(), best.getName());
            buy(times + 1);
        }
        log.info("当前买入最佳股票[{}-{}],价格:{},评分:{}", best.getCode(), best.getName(), best.getPrice(), best.getScore());
        // 等待最佳买入时机
//        int priceUpCount = 0;
//        int timesCount = 0;
//        while (timesCount < 36) {
//            SleepUtils.second(5);
//            final Double nowPrice = best.getPrice();
//            final Double lastPrice = getLastPrice(best.getCode());
//            best.setPrice(lastPrice);
//            if (lastPrice < nowPrice) {
//                priceUpCount = 0;
//                timesCount = 0;
//                log.info("最佳买入股票[{}-{}]，当前价格：{}，等待最佳买入时机", best.getCode(), best.getName(), lastPrice);
//            } else if (lastPrice > nowPrice) {
//                priceUpCount++;
//                timesCount = 0;
//                if (priceUpCount >= PRICE_UP_LIMIT) {
//                    log.info("开始买入股票[{}-{}]，卖出价格：{}", best.getCode(), best.getName(), best.getPrice());
//                    break;
//                }
//            } else {
//                priceUpCount = 0;
//                timesCount++;
//                log.info("最佳买入股票[{}-{}]，当前价格：{}，等待最佳买入时机", best.getCode(), best.getName(), lastPrice);
//            }
//        }
        final int maxBuyNumber = (int) (availableAmount / best.getPrice());
        final int buyNumber = (maxBuyNumber / 100) * 100;
        JSONObject res = buySale(BUY_TYPE_OP, best.getCode(), best.getPrice(), (double) buyNumber);
        String buyNo = res.getString("ANSWERNO");
        if (buyNo == null) {
//            List<String> errorCodes = Arrays.asList("-64", "-1");
//            String errorNo = res.getString("ERRORNO");
//            if (errorCodes.contains(errorNo)) {
//                log.info("当前股票[{}-{}]没有交易权限,更新权限数据", best.getCode(), best.getName());
//                StockInfo stockInfo = stockInfoMapper.selectByCode(best.getCode());
//                stockInfo.setPermission("0");
//                stockInfo.setUpdateTime(new Date());
//                stockInfoMapper.updateById(stockInfo);
//            }
            log.info("无法买入当前股票，尝试买入下一组股票");
            buy(times + 1);
        } else {
            // 等待10秒后后查询买入结果
            log.info("等待{}秒后查询买入结果...", WAIT_TIME_SECONDS);
            SleepUtils.second(WAIT_TIME_SECONDS);
            if (queryStatus(buyNo)) {
                // 买入成功后，保存交易数据
                final TradingRecord record = new TradingRecord();
                record.setCode(best.getCode());
                record.setName(best.getName());
                record.setBuyPrice(best.getPrice());
                record.setBuyNumber((double) buyNumber);
                record.setBuyNo(buyNo);
                final double amount = best.getPrice() * buyNumber;
                record.setBuyAmount(amount + getPeeAmount(amount));
                final Date now = new Date();
                record.setBuyDate(now);
                record.setBuyDateString(DateUtils.dateFormat.format(now));
                record.setSold("0");
                record.setCreateTime(now);
                record.setUpdateTime(now);
                // 保存选股策略ID
                ScoreStrategy strategy = strategyMapper.getSelectedStrategy();
                record.setStrategyId(strategy == null ? 0 : strategy.getId());
                record.setStrategyName(strategy == null ? "默认策略" : strategy.getName());
                tradingRecordService.save(record);
                // 更新账户资金
                updateAccountAmount();
                // 更新交易次数
                stockInfos.stream().filter(s -> s.getCode().equals(best.getCode())).forEach(s -> s.setBuySaleCount(s.getBuySaleCount() + 1));
                log.info("成功买入股票[{}-{}], 买入价格:{}，买入数量:{}，买入金额:{}", record.getCode(), record.getName(), record.getBuyPrice(), record.getBuyNumber(), record.getBuyAmount());
                // 保存评分数据
                saveDate(stockInfos);
            } else {
                // 如果交易不成功，撤单后再次尝试卖出
                log.info("当前买入交易不成功，撤单后尝试买入下一股票");
                cancelOrder(buyNo);
                SleepUtils.second(WAIT_TIME_SECONDS);
                buy(times + 1);
            }
        }
    }

    public List<StockInfo> calculateScore(List<StockInfo> dataList) {
        List<StockInfo> stockInfos = stockInfoService.list();
        stockInfos.forEach(info -> dataList.stream().filter(s -> s.getCode().equals(info.getCode())).findFirst().ifPresent(p -> {
            Double nowPrice = p.getPrice();
            List<DailyItem> priceList = JSON.parseArray(info.getPrices(), DailyItem.class);
            List<DailyItem> rateList = JSON.parseArray(info.getIncreaseRate(), DailyItem.class);
            Double score = handleScore(nowPrice, priceList, rateList);
            log.info("score:{}", score);
            info.setScore(score);
            info.setPrice(p.getPrice());
            info.setIncrease(p.getIncrease());
            info.setUpdateTime(new Date());
        }));
        return stockInfos;
    }

    public void cancelOrder(String answerNo) {
        String token = redisTemplate.opsForValue().get("requestToken");
        final long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", "111");
        paramMap.put("ContactID", answerNo);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        String param = buildParams(paramMap);
        final JSONObject result = requestUtils.request3(param);
        final String newToken = result.getString("TOKEN");
        redisTemplate.opsForValue().set("requestToken", newToken);
    }

    // 更新账户资金
    public AccountInfo updateAccountAmount() {
        String token = redisTemplate.opsForValue().get("requestToken");
        final long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 116);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        String param = buildParams(paramMap);
        final JSONArray jsonArray = requestUtils.request2(param);
        final String string = jsonArray.getString(1);
        // 币种|余额|可取|可用|总资产|证券|基金|冻结资金|资产|资金账户|币种代码|账号主副标志|
        final String[] split = string.split("\\|");
        final Double availableAmount = Double.parseDouble(split[3]);
        final Double totalAmount = Double.parseDouble(split[4]);
        final Double usedAmount = Double.parseDouble(split[5]);
        final AccountInfo accountInfo = new AccountInfo();
        accountInfo.setAvailableAmount(availableAmount);
        accountInfo.setUsedAmount(usedAmount);
        accountInfo.setTotalAmount(totalAmount);
        final Date now = new Date();
        accountInfo.setCreateTime(now);
        accountInfo.setUpdateTime(now);
        accountInfoMapper.insert(accountInfo);
        return accountInfo;
    }

    public void cancelAllOrder(int pages) {
        for (int i = 0; i < pages; i++) {
            String token = redisTemplate.opsForValue().get("requestToken");
            final long timeMillis = System.currentTimeMillis();
            HashMap<String, Object> paramMap = new HashMap<>();
            paramMap.put("action", 152);
            paramMap.put("StartPos", i * 500);
            paramMap.put("MaxCount", 500);
            paramMap.put("op_station", 4);
            paramMap.put("token", token);
            paramMap.put("reqno", timeMillis);
            JSONArray result = requestUtils.request2(buildParams(paramMap));
            if (result != null && result.size() > 1) {
                for (int j = 1; i < result.size(); j++) {
                    String string = result.getString(j);
                    String[] split = string.split("\\|");
                    String code = split[0];
                    String name = split[1];
                    String answerNo = split[8];
                    log.info("撤销当前股票[{}-{}]订单", code, name);
                    cancelOrder(answerNo);
                }
            }
        }
    }

    public JSONObject buySale(String type, String code, Double price, Double number) {
        String token = redisTemplate.opsForValue().get("requestToken");
        final long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 110);
        paramMap.put("PriceType", 0);
        paramMap.put("Direction", type);
        paramMap.put("StockCode", code);
        paramMap.put("Price", price);
        paramMap.put("Volume", number);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        final JSONObject result = requestUtils.request3(buildParams(paramMap));
        final String newToken = result.getString("TOKEN");
        redisTemplate.opsForValue().set("requestToken", newToken);
        return result;
    }

    public boolean queryStatus(String answerNo) {
        // 请求数据
        final long timeMillis = System.currentTimeMillis();
        final String token = redisTemplate.opsForValue().get("requestToken");
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 114);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", 20);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        final JSONArray results = requestUtils.request2(buildParams(paramMap));
        // 解析数据
        for (int i = 1; i < results.size(); i++) {
            final String result = results.getString(i);
            final String[] split = result.split("\\|");
            final String answerNo1 = split[6];
            final String status = split[12];
            if (answerNo1.equals(answerNo)) {
                log.info("当前订单状态：{}", status);
                return "成交".equals(status);
            }
        }
        return false;
    }

    // 获取每日最新价格数据
    public List<StockInfo> getDataList() {
        //  更新每日数据
        final List<StockInfo> stockInfos = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            // 请求数据
            HashMap<String, Object> paramMap = new HashMap<>();
            paramMap.put("c.funcno", 21000);
            paramMap.put("c.version", 1);
            paramMap.put("c.sort", 1);
            paramMap.put("c.order", 0);
            paramMap.put("c.type", "0:2:9:18");
            paramMap.put("c.curPage", i);
            paramMap.put("c.rowOfPage", 500);
            paramMap.put("c.field", "1:2:22:23:24:3:8:16:21:31");
            paramMap.put("c.cfrom", "H5");
            paramMap.put("c.tfrom", "PC");
            final JSONArray results = requestUtils.request(buildParams(paramMap));
            // 解析数据
            for (int j = 0; j < results.size(); j++) {
                final String s = results.getString(j);
                final String[] split = s.split(",");
                final String increase = split[0].replaceAll("\\[", "");
                final double increasePercent = Double.parseDouble(increase) * 100;
                final Double price = Double.parseDouble(split[1]);
                final String name = split[2].replaceAll("\"", "");
                final String market = split[3].replaceAll("\"", "");
                final String code = split[4].replaceAll("\"", "");
//                final Date now = new Date();
                final StockInfo stockInfo = new StockInfo();
                stockInfo.setName(name);
                stockInfo.setCode(code);
                stockInfo.setMarket(market);
                stockInfo.setIncrease(increasePercent);
                stockInfo.setPrice(price);
//                stockInfo.setCreateTime(now);
//                stockInfo.setUpdateTime(now);
//                stockInfo.setPermission("0");
//                stockInfo.setBuySaleCount(0);
                stockInfos.add(stockInfo);
            }
        }
        log.info("共获取到{}条新数据。", stockInfos.size());
        return stockInfos;
    }

    @SneakyThrows
    public void syncBuySaleRecord() {
        // 请求数据
        final JSONArray historyOrder = getLastMonthOrder();
        final JSONArray todayOrder = getTodayOrder();
        historyOrder.addAll(todayOrder);
        for (int i = 0; i < historyOrder.size(); i++) {
            final JSONObject order = historyOrder.getJSONObject(i);
            final String date = order.getString("date");
            final String answerNo = order.getString("answerNo");
            final String code = order.getString("code");
            final String name = order.getString("name");
            final String type = order.getString("type");
            final Double price = order.getDouble("price");
            Double number = order.getDouble("number");
            String time = order.getString("time");
            final String dateString = date + (time.length() < 6 ? ("0" + time) : time);
            if ("买入".equals(type)) {
                // 查询买入订单信息是否存在
                final LambdaQueryWrapper<TradingRecord> lambdaQueryWrapper = new QueryWrapper<TradingRecord>().lambda()
                        .eq(TradingRecord::getCode, code).like(TradingRecord::getBuyNo, answerNo);
                final TradingRecord selectedOrder = tradingRecordService.getOne(lambdaQueryWrapper);
                if (selectedOrder == null) {
                    log.info("当前股票[{}-{}]买入记录不存在，新增买入记录", name, code);
                    // 合并多个买入订单
                    final LambdaQueryWrapper<TradingRecord> queryWrapper = new QueryWrapper<TradingRecord>().lambda()
                            .eq(TradingRecord::getCode, code).eq(TradingRecord::getSold, "0");
                    final TradingRecord selectedRecord = tradingRecordService.getOne(queryWrapper);
                    if (selectedRecord == null) {
                        final TradingRecord record = new TradingRecord();
                        record.setCode(code);
                        record.setName(name);
                        record.setBuyPrice(price);
                        record.setBuyNumber(number);
                        final double amount = price * number;
                        record.setBuyAmount(amount + getPeeAmount(amount));
                        final Date buyDate = DateUtils.dateTimeFormat.parse(dateString);
                        record.setBuyDate(buyDate);
                        record.setBuyNo(answerNo);
                        record.setSold("0");
                        final Date now = new Date();
                        record.setCreateTime(now);
                        record.setUpdateTime(now);
                        tradingRecordService.save(record);
                    } else {
                        selectedRecord.setBuyPrice(price);
                        selectedRecord.setBuyNumber(number + selectedRecord.getBuyNumber());
                        final double amount = price * number;
                        selectedRecord.setBuyAmount(selectedRecord.getBuyAmount() + amount + getPeeAmount(amount));
                        final Date buyDate = DateUtils.dateTimeFormat.parse(dateString);
                        selectedRecord.setBuyDate(buyDate);
                        selectedRecord.setBuyDateString(date);
                        selectedRecord.setBuyNo(selectedRecord.getBuyNo() + "," + answerNo);
                        final Date now = new Date();
                        selectedRecord.setUpdateTime(now);
                        tradingRecordService.updateById(selectedRecord);
                    }

                }
            }
            if ("卖出".equals(type)) {
                // 查询卖出订单信息是否存在
                final LambdaQueryWrapper<TradingRecord> lambdaQueryWrapper = new QueryWrapper<TradingRecord>().lambda()
                        .eq(TradingRecord::getCode, code).eq(TradingRecord::getSaleNo, answerNo);
                final TradingRecord selectedOrder = tradingRecordService.getOne(lambdaQueryWrapper);
                if (selectedOrder == null) {
                    log.info("当前股票[{}-{}]卖出记录不存在，新增卖出记录", name, code);
                    final LambdaQueryWrapper<TradingRecord> queryWrapper = new QueryWrapper<TradingRecord>().lambda()
                            .eq(TradingRecord::getCode, code).eq(TradingRecord::getSold, "0");
                    final TradingRecord record = tradingRecordService.getOne(queryWrapper);
                    if (record == null) {
                        log.error("当前股票[{}-{}]没有查询到买入记录，卖出记录同步失败！", name, code);
                        return;
                    }
                    record.setSalePrice(price);
                    number = number < 0 ? -number : number;
                    record.setSaleNumber(number);
                    final double amount = price * number;
                    record.setSaleAmount(amount - getPeeAmount(amount));
                    final Date saleDate = DateUtils.dateTimeFormat.parse(dateString);
                    record.setSaleDate(saleDate);
                    record.setSaleDateString(date);
                    record.setSold("1");
                    record.setSaleNo(answerNo);
                    final Date now = new Date();
                    record.setUpdateTime(now);
                    // 计算收益和日收益率
                    double income = record.getSaleAmount() - record.getBuyAmount();
                    record.setIncome(income);
                    int dateDiff = diffDate(record.getBuyDate(), record.getSaleDate());
                    record.setHoldDays(dateDiff);
                    double incomeRate = income / record.getBuyAmount() * 100;
                    record.setIncomeRate(incomeRate);
                    final double dailyIncomeRate = incomeRate / dateDiff;
                    record.setDailyIncomeRate(dailyIncomeRate);
                    tradingRecordService.update(record, queryWrapper);
                }
            }
        }
    }

    @SneakyThrows
    public void syncBuySaleCount() {
        List<TradingRecord> list = tradingRecordService.list();
        Map<String, IntSummaryStatistics> collect = list.stream().collect(Collectors.groupingBy(TradingRecord::getCode, Collectors.summarizingInt((o) -> "1".equals(o.getSold()) ? 2 : 1)));
        collect.forEach((code, accumulate) -> {
            StockInfo stockInfo = new StockInfo();
            stockInfo.setBuySaleCount((int) accumulate.getSum());
            stockInfoService.update(stockInfo, new QueryWrapper<StockInfo>().lambda().eq(StockInfo::getCode, code));
        });
        log.info("共同步{}条股票交易记录", collect.size());
    }

    @SneakyThrows
    public void updateHistoryPrice() {
        final List<StockInfo> stockInfos = stockInfoService.list();
        final CountDownLatch countDownLatch = new CountDownLatch(stockInfos.size());
        // 多线程请求数据
        ArrayList<StockInfo> saveList = new ArrayList<>();
        stockInfos.forEach(s -> threadPool.submit(() -> {
            List<DailyItem> historyPrices = getHistoryPrices(s.getCode());
            List<DailyItem> rateList = getRateList(historyPrices);
            s.setPrices(JSON.toJSONString(historyPrices));
            s.setIncreaseRate(JSON.toJSONString(rateList));
            s.setUpdateTime(new Date());
            saveList.add(s);
            final long finishNums = stockInfos.size() - countDownLatch.getCount() + 1;
            if (finishNums % 100 == 0) {
                log.info("已完成{}个获取股票历史价格任务,剩余{}个任务", finishNums, countDownLatch.getCount() + 1);
            }
            countDownLatch.countDown();
        }));
        countDownLatch.await();
        saveDate(saveList);
    }

    @SneakyThrows
    public void updateNowPrice() {
        List<StockInfo> newInfos = getDataList();
        final List<StockInfo> dataList = stockInfoService.list();
        final ArrayList<StockInfo> saveList = new ArrayList<>();
        newInfos.forEach(newInfo -> {
            AtomicBoolean exist = new AtomicBoolean(false);
            dataList.stream().filter(s -> s.getCode().equals(newInfo.getCode())).findFirst().ifPresent(p -> {
                Double nowPrice = newInfo.getPrice();
                List<DailyItem> priceList = JSON.parseArray(p.getPrices(), DailyItem.class);
                List<DailyItem> rateList = JSON.parseArray(p.getIncreaseRate(), DailyItem.class);
                Double score = handleScore(nowPrice, priceList, rateList);
                p.setScore(score);
                p.setPrice(newInfo.getPrice());
                p.setIncrease(newInfo.getIncrease());
                p.setUpdateTime(new Date());
                saveList.add(p);
                exist.set(true);
            });
            if (!exist.get()) {
                Date now = new Date();
                newInfo.setCreateTime(now);
                newInfo.setUpdateTime(now);
                newInfo.setPermission("1");
                newInfo.setBuySaleCount(0);
                newInfo.setScore(0.0);
                saveList.add(newInfo);
            }
        });
        saveDate(saveList);
    }

    private Double handleScore(Double nowPrice, List<DailyItem> priceList, List<DailyItem> rateList) {
        if (priceList != null && priceList.size() > 0) {
            Double prePrice = priceList.get(priceList.size() - 1).getItem();
            double increaseRate = (nowPrice - prePrice) / prePrice;
            priceList.add(new DailyItem(DateUtils.dateFormat.format(new Date()), nowPrice));
            rateList.add(new DailyItem(DateUtils.dateFormat.format(new Date()), increaseRate));
            List<Double> prices = priceList.stream().map(DailyItem::getItem).collect(Collectors.toList());
            List<Double> rates = rateList.stream().map(DailyItem::getItem).collect(Collectors.toList());
            return getScoreByList(prices, rates);
        }
        return 0.0;
    }


    @SneakyThrows
    public void saveDate(List<StockInfo> dataList) {
        log.info("开始更新数据库......");
        if (CollectionUtils.isNotEmpty(dataList)) {
            // 多线程批量更新数据库
            int pages = dataList.size() / UPDATE_BATCH_SIZE + 1;
            CountDownLatch countDownLatch = new CountDownLatch(pages);
            log.info("共{}条数据, 分{}页进行批量更新,每页{}条数据", dataList.size(), pages, UPDATE_BATCH_SIZE);
            for (int i = 1; i <= pages; i++) {
                int finalI = i;
                threadPool.submit(() -> {
                    long startIndex = (long) (finalI - 1) * UPDATE_BATCH_SIZE;
                    final List<StockInfo> saveList = dataList.stream().skip(startIndex).limit(UPDATE_BATCH_SIZE).collect(Collectors.toList());
                    stockInfoService.saveOrUpdateBatch(saveList);
                    long endIndex = startIndex + UPDATE_BATCH_SIZE;
                    endIndex = Math.min(endIndex, dataList.size());
                    log.info("第{}个数据更新任务处理完成，任务更新范围[{},{}]内,共{}条数", pages - countDownLatch.getCount() + 1, startIndex, endIndex, endIndex - startIndex);
                    countDownLatch.countDown();
                });
            }
            countDownLatch.await();
            log.info("数据库更新完成......");
        }
    }

    // 获取历史价格曲线
    public List<DailyItem> getHistoryPrices(String code) {
        // 获取历史数据
        HashMap<String, Object> paramMap = new HashMap<>(10);
        paramMap.put("c.funcno", 20009);
        paramMap.put("c.version", 1);
        paramMap.put("c.stock_code", code);
        paramMap.put("c.type", "day");
        paramMap.put("c.count", "20");
        paramMap.put("c.cfrom", "H5");
        paramMap.put("c.tfrom", "PC");
        final JSONArray results = requestUtils.request(buildParams(paramMap));
        final ArrayList<DailyItem> prices = new ArrayList<>();
        int startIndex = results.size() >= HISTORY_PRICE_LIMIT ? results.size() - HISTORY_PRICE_LIMIT : 0;
        for (int i = startIndex; i < results.size(); i++) {
            final String s = results.getString(i);
            final String[] split = s.split(",");
            final String date = split[0].replaceAll("\\[", "");
            final String price1 = split[1];
            final String price2 = split[2];
            final String price3 = split[3];
            final String price4 = split[4].replaceAll("]", "");
//            prices.add(new DailyItem(date.concat("-1"), Double.parseDouble(price1) / 100));
//            prices.add(new DailyItem(date.concat("-2"), Double.parseDouble(price2) / 100));
//            prices.add(new DailyItem(date.concat("-3"), Double.parseDouble(price3) / 100));
            prices.add(new DailyItem(date, Double.parseDouble(price1) / 100));
        }
        return prices;
    }

    // 获取最近一个月的成交订单订单
    public JSONArray getLastMonthOrder() {
// 请求数据
        final long timeMillis = System.currentTimeMillis();
        final String token = redisTemplate.opsForValue().get("requestToken");
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        final String end = simpleDateFormat.format(calendar.getTime());
        calendar.add(Calendar.MONTH, -1);
        final String start = simpleDateFormat.format(calendar.getTime());
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 115);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", 100);
        paramMap.put("BeginDate", start);
        paramMap.put("EndDate", end);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        final JSONArray results = requestUtils.request2(buildParams(paramMap));
        final JSONArray jsonArray = new JSONArray();
        for (int i = 1; i < results.size(); i++) {
            final String result = results.getString(i);
            final String[] split = result.split("\\|");
            final String date = split[0];
            final String answerNo = split[1];
            if ("0".equals(answerNo)) {
                // 合同编号为0非买卖信息，跳过处理
                continue;
            }
            final String code = split[4];
            final String name = split[5];
            final String type = split[6];
            final String price = split[7];
            final String number = split[8];
            final String time = split[11];
            final JSONObject jsonObject = new JSONObject();
            jsonObject.put("date", date);
            jsonObject.put("answerNo", answerNo);
            jsonObject.put("code", code);
            jsonObject.put("name", name);
            jsonObject.put("type", type);
            jsonObject.put("price", price);
            jsonObject.put("number", number);
            jsonObject.put("time", time);
            jsonArray.add(jsonObject);
        }
        return jsonArray;
    }

    // 获取今日成交订单
    public JSONArray getTodayOrder() {
// 请求数据
        final long timeMillis = System.currentTimeMillis();
        final String token = redisTemplate.opsForValue().get("requestToken");
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 114);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", 100);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        final JSONArray results = requestUtils.request2(buildParams(paramMap));
// 解析数据:  名称|买卖方向|数量|价格|成交金额|代码|合同编号|成交编号|股东帐号|市场类型|日期|时间|成交状态|
        final JSONArray jsonArray = new JSONArray();
        for (int i = 1; i < results.size(); i++) {
            final String result = results.getString(i);
            final String[] split = result.split("\\|");
            final String name = split[0];
            final String type = split[1];
            final String number = split[2];
            final String price = split[3];
            final String code = split[5];
            final String answerNo = split[6];
            final String date = split[10];
            final String time = split[11];
            final JSONObject jsonObject = new JSONObject();
            jsonObject.put("date", date);
            jsonObject.put("code", code);
            jsonObject.put("answerNo", answerNo);
            jsonObject.put("name", name);
            jsonObject.put("type", type);
            jsonObject.put("price", price);
            jsonObject.put("number", number);
            jsonObject.put("time", time);
            jsonArray.add(jsonObject);
        }
        return jsonArray;
    }

    // 计算价格日增长率曲线
    public List<DailyItem> getRateList(List<DailyItem> prices) {
        final ArrayList<DailyItem> rateList = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            final Double price = prices.get(i).getItem();
            final Double oldPrice = prices.get(i - 1).getItem();
            final double dailyIncreaseRate = oldPrice == 0 ? 0 : (price - oldPrice) / oldPrice;
            // 增长率乘100方便计算，否则数值太小
            double increaseRate = Double.parseDouble(String.format("%.4f", dailyIncreaseRate * 100));
            rateList.add(new DailyItem(prices.get(i).getDate(), increaseRate));
        }
        return rateList;
    }

    // 获取最近10个交易日天的日增长率，用以计算增长的稳定性和增长率总和
    // 价格稳定性用方差来衡量,增长率总和体现增长幅度
    // 增长率总和，乘以占比系数(index/size)，离当前时间越近,趋近于1，离当前时间越远，系数趋近于0
    // 增长的天数越多，日增长率总和越大，价格方差越小，增长波动越小，代表稳定增长，评分越高
    public Double getScoreByList(List<Double> priceList, List<Double> rateList) {
        if (priceList.isEmpty() || rateList.isEmpty()) {
            return 0.0;
        }
        // 获取策略默认值
        StrategyParams strategyParams = new StrategyParams(0.5, 5, 50);
        ScoreStrategy strategy = strategyMapper.getSelectedStrategy();
        if (strategy != null) {
            try {
                String params = strategy.getParams();
                strategyParams = JSON.parseObject(params, StrategyParams.class);
            } catch (Exception e) {
                log.warn("策略参数解析异常，使用默认策略参数{}", strategyParams);
            }
        }
        Double preRateFactor = strategyParams.getPreRateFactor();
        Integer priceTolerance = strategyParams.getPriceTolerance();
        Integer historyLimit = strategyParams.getHistoryLimit();
        List<Double> limitPriceList = priceList.stream().skip(priceList.size() > historyLimit ? priceList.size() - historyLimit : 0).limit(historyLimit).collect(Collectors.toList());
        List<Double> limitRateList = rateList.stream().skip(rateList.size() > historyLimit ? rateList.size() - historyLimit : 0).limit(historyLimit).collect(Collectors.toList());
        // 计算日增长率平均值总和
        double sumRate = 0;
        final int size = limitRateList.size();
        for (int i = 1; i <= size; i++) {
            // 修改前期数据对得分的影响，保证系数范围在[0.5,1]之间
            // 数值越小，前期数据对得分影响越低
            // 有常数阶和线性阶两种，可供调试
            final double f = ((double) i / size) * (1 - preRateFactor) + preRateFactor;
//            final double f = 1;
            sumRate += limitRateList.get(i - 1) * f;
        }
        // 计算价格方差
        Double priceSum = limitPriceList.stream().reduce(Double::sum).orElse(0.0);
        final double priceAvg = priceSum / limitPriceList.size();
        double sum = 0;
        for (Double price : limitPriceList) {
            sum += Math.pow((price - priceAvg), 2);
        }
        // 方差
        double sqrt = Math.sqrt(sum / priceList.size());
        // 价格波动系数(1 - sqrt / PRICE_BASE_NUMBER)
        // 方差范围限制，确保价格波动系数不为负数
        // 方差越趋向于0，价格波动系数越趋向于1，说明价格越稳定，得分越高
        // 方越趋向于 PRICE_BASE_NUMBER(价格波动容忍度)，
        // 价格波动系数越趋向于0，说明价格波动越大，得分越低
        sqrt = Math.min(sqrt, priceTolerance);
        return sumRate * (1 - sqrt / priceTolerance);
    }

    // 计算交易日期差
    public int diffDate(Date date1, Date date2) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
        HashMap<String, Integer> dateMap = getDateMap();
        Integer from = dateMap.get(DateUtils.dateFormat.format(date1));
        Integer to = dateMap.get(simpleDateFormat.format(date2));
        return Math.max(to - from, 1);
    }

    // 计算手续费，万五，最低五元
    public Double getPeeAmount(Double amount) {
        return Math.max(5, amount * 0.0005);
    }

    @SneakyThrows
    public HashMap<String, Integer> getDateMap() {
        if (dateMap == null) {
            InputStream is = new ClassPathResource("/json/date.txt").getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            final StringBuilder stringBuilder = new StringBuilder();
            String s;
            while ((s = reader.readLine()) != null) {
                stringBuilder.append(s);
            }
            String[] split = stringBuilder.toString().split(",");
            dateMap = new HashMap<>();
            int index = 0;
            for (String dateString : split) {
                String[] split1 = dateString.split("-");
                int weekDay = Integer.parseInt(split1[1]);
                // 非交易日 不计算日期差
                if (weekDay >= 1 && weekDay <= 5) {
                    index++;
                }
                dateMap.put(split1[0], index);
            }
        }
        return dateMap;
    }

}
