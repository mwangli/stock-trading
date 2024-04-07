package online.mwang.stockTrading.web.job;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.*;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import online.mwang.stockTrading.web.mapper.PredictPriceMapper;
import online.mwang.stockTrading.web.mapper.ScoreStrategyMapper;
import online.mwang.stockTrading.web.mapper.StockInfoMapper;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import online.mwang.stockTrading.web.utils.OcrUtils;
import online.mwang.stockTrading.web.utils.RequestUtils;
import online.mwang.stockTrading.web.utils.SleepUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 10:32
 * @description: AllJobs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllJobs {

    public static final String TOKEN = "requestToken";
    public static final int LOGIN_RETRY_TIMES = 10;
    public static final double PRICE_TOTAL_FALL_LIMIT = -5.0;
    public static final double PRICE_TOTAL_UPPER_LIMIT = 5.0;
    public static final int WAIT_TIME_SECONDS = 10;
    public static final int HISTORY_PRICE_LIMIT = 100;
    public static final int UPDATE_BATCH_SIZE = 500;
    public static final int THREAD_POOL_NUMBERS = 8;
    public static final int TOKEN_EXPIRE_MINUTES = 30;
    public static final int CANCEL_WAIT_TIMES = 30;
    public static final String COLLECTION_NAME_PREFIX = "code_";
    public static final String BUY_TYPE_OP = "B";
    public static final String SALE_TYPE_OP = "S";
    public static HashMap<String, Integer> dateMap;
    public final RequestUtils requestUtils;
    public final OcrUtils ocrUtils;
    public final StockInfoService stockInfoService;
    public final TradingRecordService tradingRecordService;
    public final AccountInfoMapper accountInfoMapper;
    public final StringRedisTemplate redisTemplate;
    public final MongoTemplate mongoTemplate;
    public final StockInfoMapper stockInfoMapper;
    public final ScoreStrategyMapper strategyMapper;
    public final SleepUtils sleepUtils;
    public final PredictPriceMapper predictPriceMapper;
    public final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_NUMBERS);
    public boolean enableSaleWaiting = true;
    public boolean enableBuyWaiting = true;
    public String resourceBaseDir = "src/main/resources/";

    @Value("${PROFILE}")
    private String profile;


    public HashMap<String, Object> buildParams(HashMap<String, Object> paramMap) {
        if (paramMap == null) return new HashMap<>();
        paramMap.put("cfrom", "H5");
        paramMap.put("tfrom", "PC");
        paramMap.put("newindex", "1");
        paramMap.put("MobileCode", "13278828091");
        paramMap.put("intacttoserver", "@ClZvbHVtZUluZm8JAAAANTI1QS00Qjc4");
        return paramMap;
    }

    public String getToken() {
        final String token = redisTemplate.opsForValue().get(TOKEN);
        if (token == null) {
            tryLogin();
        }
        return redisTemplate.opsForValue().get(TOKEN);
    }

    public void setToken(String token) {
        if (token == null) {
            return;
        }
        redisTemplate.opsForValue().set(TOKEN, token, TOKEN_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }

    public void clearToken() {
        redisTemplate.opsForValue().getAndDelete(TOKEN);
    }

    @SneakyThrows
    public String getCheckCodeFromMessage(String message) {
        String code = "1234";
        try {
            code = ocrUtils.execute(message);
            log.info("识别到图片验证码:{}", code);
        } catch (Exception e) {
            log.error("验证码识别异常!");
        }
        return code;
    }

    @SneakyThrows
    public List<String> getCheckCode() {
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", "41092");
        final JSONObject res = requestUtils.request(buildParams(paramMap));
        final String checkToken = res.getString("CHECKTOKEN");
        final String checkMessage = res.getString("MESSAGE");
        final String checkCode = getCheckCodeFromMessage(checkMessage);
        return Arrays.asList(checkCode, checkToken);
    }

    @SneakyThrows
    public void tryLogin() {
        int time = 0;
        while (time++ < LOGIN_RETRY_TIMES) {
            log.info("第{}尝试登录------", time);
            Boolean success = doLogin();
            if (success == null) {
                log.info("登录失败！");
                return;
            }
            if (success) {
                log.info("登录成功！");
                return;
            } else {
                log.info("验证码错误,尝试重新登录!");
            }
        }
        log.info("尝试{}次后登录失败！请检查程序代码！", LOGIN_RETRY_TIMES);
    }

    public Boolean doLogin() {
        final List<String> checkCode = getCheckCode();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", "100");
        paramMap.put("modulus_id", "2");
        paramMap.put("accounttype", "ZJACCOUNT");
        paramMap.put("account", "880008900626");
        paramMap.put("MobileCode", "13278828091");
        paramMap.put("password", "9da08684daf6cda30ef11b8fecbaa568963749de0a0496e23fcfca4b9b29953e9703b1a7cc9fe9259f7ad732f2fa1bd09017ba884c0817667c458cb554dcbeb480623ad070c4d81a185dd997e45c9ab5ae655584728193759ec40e72ad7e25f833773f3e6cd0d23c16493765358adc593ce0688edccbefdd35a3355016b724fc");
        paramMap.put("signkey", "51cfce1626c7cb087b940a0c224f2caa");
        paramMap.put("CheckCode", checkCode.get(0));
        paramMap.put("CheckToken", checkCode.get(1));
        final JSONObject result = requestUtils.request(buildParams(paramMap));
        final String errorNo = result.getString("ERRORNO");
        if ("331100".equals(errorNo)) {
            setToken(result.getString("TOKEN"));
            return true;
        }
        if ("-330203".equals(errorNo)) {
            // 验证码错误,继续尝试登录
            return false;
        }
        // 其他错误直接返回
        return null;
    }

    public AccountInfo getAmount() {
        String token = getToken();
        final long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 116);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        final JSONArray jsonArray = requestUtils.request2(buildParams(paramMap));
        if (jsonArray == null || jsonArray.size() < 1) {
            log.info("获取账户资金失败！");
            return null;
        }
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
        log.info("当前可用金额:{}元,持仓金额:{}元,总金额:{}元。", availableAmount, usedAmount, totalAmount);
        return accountInfo;
    }

    private List<TradingRecord> getHoldList() {
        String token = getToken();
        long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", "117");
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", 500);
        paramMap.put("reqno", timeMillis);
        paramMap.put("token", token);
        paramMap.put("Volume", 100);
        JSONArray results = requestUtils.request2(buildParams(paramMap));
        List<TradingRecord> dataList = new ArrayList<>();
        if (results == null || results.size() <= 1) {
            return dataList;
        }
        for (int i = 1; i < results.size(); i++) {
            String data = results.getString(i);
            String[] split = data.split("\\|");
            if ("0.00".equals(split[1]) || "0.00".equals(split[2])) {
                continue;
            }
            TradingRecord record = new TradingRecord();
            record.setCode(split[9]);
            record.setName(split[0]);
            record.setSalePrice(Double.parseDouble(split[4]));
            record.setSaleNumber(Double.parseDouble(split[2]));
            dataList.add(record);
        }
        return dataList;
    }

    protected Boolean checkSoldToday(String sold) {
        // 检查今天是否有交易记录,防止重复交易
        LambdaQueryWrapper<TradingRecord> queryWrapper = new LambdaQueryWrapper<TradingRecord>()
                .eq("0".equals(sold), TradingRecord::getBuyDateString, DateUtils.dateFormat.format(new Date()))
                .eq("1".equals(sold), TradingRecord::getSaleDateString, DateUtils.dateFormat.format(new Date()))
                .eq(TradingRecord::getSold, sold);
        List<TradingRecord> hasSold = tradingRecordService.list(queryWrapper);
        return CollectionUtils.isNotEmpty(hasSold);
    }

    public Boolean waitOrderCancel() {
        int times = 0;
        while (times++ < CANCEL_WAIT_TIMES) {
            sleepUtils.second(WAIT_TIME_SECONDS);
            List<OrderStatus> todayOrders = listTodayOrder();
//            List<OrderStatus> historyOrders = listHistoryOrder();
//            todayOrders.addAll(historyOrders);
            List<String> cancelStatus = Arrays.asList("已报", "已报待撤");
            List<OrderStatus> orderList = todayOrders.stream().filter(o -> cancelStatus.contains(o.getStatus())).collect(Collectors.toList());
            if (orderList.size() == 0) {
                return true;
            } else {
                log.info("待撤销订单:{}", orderList);
                orderList.forEach(o -> cancelOrder(o.getAnswerNo()));
            }
        }
        return false;
    }

    protected Boolean checkBuyCode(String code) {
        // 检查今天是否有重复code,防止买入相同股票
        LambdaQueryWrapper<TradingRecord> queryWrapper = new LambdaQueryWrapper<TradingRecord>()
//                .eq(TradingRecord::getSold, "0")
                .eq(TradingRecord::getCode, code);
        List<TradingRecord> hasSold = tradingRecordService.list(queryWrapper);
        return CollectionUtils.isNotEmpty(hasSold);
    }

    protected StockInfo waitingBestPrice(StockInfo best, String runningId) {
        double lastPrice = getLastPrice(best.getCode());
        double totalPercent = 0.0;
        while (inTradingTimes()) {
            sleepUtils.minutes(1, runningId);
            Double nowPrice = getLastPrice(best.getCode());
            final double price = nowPrice - lastPrice;
            final double pricePercent = price * 100 / nowPrice;
            if (pricePercent < 0) {
                totalPercent += pricePercent;
            }
            log.info("最佳买入股票[{}-{}],上次价格:{},当前价格:{},当前增长幅度:{}%,总增长幅度:{}%,等待最佳买入时机...",
                    best.getCode(), best.getName(), lastPrice, nowPrice, String.format("%.4f", pricePercent), String.format("%.4f", totalPercent));
            lastPrice = nowPrice;
            // 3交易时间段内，总增长幅度达到阈值，或者交易时间即将结束
            boolean totalCondition = totalPercent <= PRICE_TOTAL_FALL_LIMIT;
            if (isDeadLine() || totalCondition) {
                if (isDeadLine()) {
                    log.info("今日交易时间即将结束，开始买入股票。");
                } else {
                    log.info("最佳买入股票[{}-{}],总增长幅度达到{}%,开始买入股票。",
                            best.getCode(), best.getName(), PRICE_TOTAL_FALL_LIMIT);
                }
                best.setPrice(lastPrice);
                return best;
            }
        }
        return null;
    }


    protected TradingRecord getBestRecord() {
        List<TradingRecord> holdList = getHoldList();
        double maxDailyRate = -100.00;
        TradingRecord best = null;
        for (TradingRecord record : holdList) {
            // 查询买入时间
            final LambdaQueryWrapper<TradingRecord> queryWrapper = new LambdaQueryWrapper<TradingRecord>().eq(TradingRecord::getCode, record.getCode()).eq(TradingRecord::getSold, "0");
            TradingRecord selectRecord = tradingRecordService.getOne(queryWrapper);
            if (selectRecord == null) {
                log.error("当前股票[{}-{}]未查询到买入记录,需要手动触发订单同步任务", record.getCode(), record.getName());
                continue;
            }
            // 更新每日数据
            final double amount = record.getSalePrice() * record.getSaleNumber();
            double saleAmount = amount - getPeeAmount(amount);
            double income = saleAmount - selectRecord.getBuyAmount();
            int dateDiff = diffDate(selectRecord.getBuyDate(), new Date());
            double incomeRate = income / selectRecord.getBuyAmount() * 100;
            double dailyIncomeRate = incomeRate / dateDiff;
            if (dailyIncomeRate > maxDailyRate) {
                selectRecord.setSalePrice(record.getSalePrice());
                selectRecord.setSaleNumber(selectRecord.getBuyNumber());
                selectRecord.setSaleAmount(saleAmount);
                selectRecord.setIncome(income);
                selectRecord.setIncomeRate(incomeRate);
                selectRecord.setHoldDays(dateDiff);
                selectRecord.setDailyIncomeRate(dailyIncomeRate);
                maxDailyRate = dailyIncomeRate;
                best = selectRecord;
            }
        }
        return best;
    }

    protected TradingRecord waitingBestRecord(TradingRecord best, String runningId) {
        double totalPercent = 0.0;
        while (inTradingTimes()) {
            sleepUtils.minutes(1, runningId);
            TradingRecord bestRecord = getBestRecord();
            if (bestRecord == null) {
                log.info("最佳卖出股票获取异常");
                return null;
            }
            // 如果最佳股发生变化
            if (!bestRecord.getCode().equals(best.getCode())) {
                totalPercent = 0;
                best = bestRecord;
                log.info("最佳卖出股票变化为:[{}-{}]", best.getCode(), best.getName());
            }
            Double lastPrice = best.getSalePrice();
            final double price = bestRecord.getSalePrice() - lastPrice;
            final double pricePercent = price * 100 / lastPrice;
            if (pricePercent > 0) {
                totalPercent += pricePercent;
            }
            best = bestRecord;
            log.info("最佳卖出股票[{}-{}],买入价格:{},上次价格:{},当前价格:{},当前增长幅度:{}%,总增长幅度:{}%,等待最佳卖出时机...",
                    best.getCode(), best.getName(), best.getBuyPrice(), lastPrice, best.getSalePrice(), String.format("%.4f", pricePercent), String.format("%.4f", totalPercent));
            // 交易时间段内，价格总增长幅度达到阈值，或者交易时间即将结束
            boolean totalCondition = totalPercent >= PRICE_TOTAL_UPPER_LIMIT;
            boolean priceCondition = isDeadLine() || totalCondition;
            boolean incomeCondition = best.getSalePrice() - best.getBuyPrice() > 0.1;
            boolean saleCondition = incomeCondition && priceCondition;
            if (isMorning() ? saleCondition : priceCondition) {
                if (isDeadLine()) {
                    log.info("今日交易时间即将结束，开始卖出股票。");
                } else {
                    log.info("最佳卖出股票[{}-{}],总增长幅度达到{}%,开始卖出股票。",
                            best.getCode(), best.getName(), PRICE_TOTAL_UPPER_LIMIT);
                }
                return best;
            }
        }
        return null;
    }


    protected List<OrderStatus> listTodayOrder() {
        String token = getToken();
        final long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 113);
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", 100);
        paramMap.put("token", token);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("reqno", timeMillis);
        JSONArray result = requestUtils.request2(buildParams(paramMap));
        return arrayToList(result, true);
    }

    private Boolean isMorning() {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        final int hours = calendar.get(Calendar.HOUR_OF_DAY);
        return hours < 12;
    }

    private Boolean isDeadLine() {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        final int hours = calendar.get(Calendar.HOUR_OF_DAY);
        final int minutes = calendar.get(Calendar.MINUTE);
        return hours >= 14 && minutes >= 50;
    }

    public Boolean inTradingTimes() {
        return inTradingTimes1() || inTradingTimes2();
    }

    public Boolean inTradingTimes1() {
        String format = DateUtils.timeFormat.format(new Date());
        return format.compareTo("09:30") >= 0 && format.compareTo("11:30") <= 0;
    }

    public Boolean inTradingTimes2() {
        String format = DateUtils.timeFormat.format(new Date());
        return format.compareTo("13:00") >= 0 && format.compareTo("15:00") <= 0;
    }

    protected Double getLastPrice(String code) {
        long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("stockcode", code);
        paramMap.put("Reqno", timeMillis);
        paramMap.put("action", 33);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("Level", 1);
        paramMap.put("UseBPrice", 1);
        JSONObject res = requestUtils.request(buildParams(paramMap));
        return res.getDouble("PRICE");
    }


    protected List<OrderStatus> arrayToList(JSONArray result, boolean isToday) {
        // 委托日期|时间|证券代码|证券|委托类别|买卖方向|状态|委托|数量|委托编号|均价|成交|股东代码|交易类别|
        ArrayList<OrderStatus> statusList = new ArrayList<>();
        if (result != null && result.size() > 1) {
            for (int i = 1; i < result.size(); i++) {
                String string = result.getString(i);
                String[] split = string.split("\\|");
                String code = isToday ? split[0] : split[2];
                String name = isToday ? split[1] : split[3];
                String status = isToday ? split[2] : split[6];
                String answerNo = isToday ? split[8] : split[9];
                OrderStatus orderStatus = new OrderStatus(answerNo, code, name, status);
                statusList.add(orderStatus);
            }
        }
        return statusList;
    }

    protected List<OrderStatus> pageCancelOrder(int page) {
        String token = getToken();
        final long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 152);
        paramMap.put("StartPos", page * 500);
        paramMap.put("MaxCount", 500);
        paramMap.put("op_station", 4);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        JSONArray result = requestUtils.request2(buildParams(paramMap));
        return arrayToList(result, true);
    }

    protected List<OrderStatus> listCancelOrder() {
        List<OrderStatus> cancelOrders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cancelOrders.addAll(pageCancelOrder(i));
        }
        return cancelOrders;
    }

    public AccountInfo getAccountAmount(AccountInfo accountInfo) {
        // 计算已用金额
        double usedAmount = tradingRecordService.list(new LambdaQueryWrapper<TradingRecord>().eq(TradingRecord::getSold, "0")).stream().mapToDouble(TradingRecord::getBuyAmount).sum();
        accountInfo.setUsedAmount(usedAmount);
        accountInfo.setTotalAmount(accountInfo.getAvailableAmount() + usedAmount);
        return accountInfo;
    }

    public void cancelOrder(String answerNo) {
        String token = getToken();
        final long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", "111");
        paramMap.put("ContactID", answerNo);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        final JSONObject result = requestUtils.request(buildParams(paramMap));
    }

    protected void cancelAllOrder() {
        List<OrderStatus> orderList = listCancelOrder();
        log.info("待撤销订单:{}", orderList);
        orderList.forEach(o -> cancelOrder(o.getAnswerNo()));
    }


    public List<OrderStatus> listHistoryOrder() {
        String token = getToken();
        final long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 5018);
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", 100);
        paramMap.put("token", token);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("reqno", timeMillis);
        JSONArray result = requestUtils.request2(buildParams(paramMap));
        return arrayToList(result, false);
    }


    public String queryOrderStatus(String answerNo) {
        List<OrderStatus> orderInfos = listTodayOrder();
        log.info("查询到订单状态信息:");
        orderInfos.forEach(o -> log.info("{}-{}:{}", o.getCode(), o.getName(), o.getStatus()));
        Optional<OrderStatus> status = orderInfos.stream().filter(o -> o.getAnswerNo().equals(answerNo)).findFirst();
        if (status.isPresent()) {
            log.info("未查询到合同编号为{}的订单交易状态！", answerNo);
            return null;
        }
        return status.get().getStatus();
    }


    public Boolean waitOrderCancel(String answerNo) {
        int times = 0;
        while (times++ < CANCEL_WAIT_TIMES) {
            sleepUtils.second(WAIT_TIME_SECONDS);
            final String status = queryOrderStatus(answerNo);
            if (status == null) {
                log.info("当前合同编号:{},订单状态查询失败。", answerNo);
                return null;
            }
            if ("已成".equals(status)) {
                log.info("当前合同编号:{},交易成功。", answerNo);
                return true;
            }
            if ("已报".equals(status)) {
                log.info("当前合同编号:{},交易不成功,进行撤单操作。", answerNo);
                cancelOrder(answerNo);
            }
            if ("已报待撤".equals(status)) {
                log.info("当前合同编号:{},等待撤单完成...", answerNo);
            }
            if ("已撤".equals(status)) {
                log.info("当前合同编号:{},订单撤销完成", answerNo);
                return false;
            }
            if ("废单".equals(status)) {
                log.info("当前合同编号:{},订单已经废除", answerNo);
                return false;
            }
        }
        return null;
    }


    public JSONObject buySale(String type, String code, Double price, Double number) {
        String token = getToken();
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
        final JSONObject result = requestUtils.request(buildParams(paramMap));
        setToken(result.getString("TOKEN"));
        return result;
    }

    // 获取每日最新价格数据
    public List<StockInfo> getDataList() {
        final List<StockInfo> stockInfos = new ArrayList<>();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("c.funcno", 21000);
        paramMap.put("c.version", 1);
        paramMap.put("c.sort", 1);
        paramMap.put("c.order", 1);
        paramMap.put("c.type", "0:2:9:18");
        paramMap.put("c.curPage", 1);
        paramMap.put("c.rowOfPage", 5000);
        paramMap.put("c.field", "1:2:22:23:24:3:8:16:21:31");
        paramMap.put("c.cfrom", "H5");
        paramMap.put("c.tfrom", "PC");
        final JSONArray results = requestUtils.request3(buildParams(paramMap));
        for (int j = 0; j < results.size(); j++) {
            final String s = results.getString(j);
            final String[] split = s.split(",");
            final String increase = split[0].replaceAll("\\[", "");
            final double increasePercent = Double.parseDouble(increase) * 100;
            final Double price = Double.parseDouble(split[1]);
            final String name = split[2].replaceAll("\"", "");
            final String market = split[3].replaceAll("\"", "");
            final String code = split[4].replaceAll("\"", "");
            final StockInfo stockInfo = new StockInfo();
            stockInfo.setName(name);
            stockInfo.setCode(code);
            stockInfo.setMarket(market);
            stockInfo.setIncrease(increasePercent);
            stockInfo.setPrice(price);
            stockInfos.add(stockInfo);
        }
        log.info("共获取到{}条新数据。", stockInfos.size());
        return stockInfos;
    }


    /**
     * 2024-04-06，采用mongo来存储历史数据，减轻数据压力
     *
     * @See {AllJobs.updateLastHistoryPrice}
     */
    @SneakyThrows
    @Deprecated
    public void updateHistoryPrice() {
        LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getDeleted, "1");
        final List<StockInfo> stockInfos = stockInfoMapper.selectList(queryWrapper);
        final CountDownLatch countDownLatch = new CountDownLatch(stockInfos.size());
        ArrayList<StockInfo> saveList = new ArrayList<>();
        stockInfos.forEach(s -> threadPool.submit(() -> {
            try {
                List<DailyItem> historyPrices = getHistoryPrices(s.getCode());
                List<DailyItem> rateList = getRateList(historyPrices);
                s.setPrices(JSON.toJSONString(historyPrices));
                s.setIncreaseRate(JSON.toJSONString(rateList));
                s.setUpdateTime(new Date());
                saveList.add(s);
                final long finishNums = stockInfos.size() - countDownLatch.getCount();
                if (finishNums % 100 == 0) {
                    log.info("已完成{}个获取股票历史价格任务,剩余{}个任务", finishNums, countDownLatch.getCount());
                }
            } catch (Exception e) {
                e.printStackTrace();
                log.error("获取数据异常:{}", e.getMessage());
            } finally {
                countDownLatch.countDown();
            }
        }));
        countDownLatch.await();
        saveDate(saveList);
        List<StockInfo> deletedList = stockInfoMapper.selectList(new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getDeleted, "0"));
        deletedList.forEach(d -> {
            log.info("清除退市股票[{}-{}]", d.getCode(), d.getName());
            stockInfoMapper.deleteByCode(d.getCode());
        });
    }

    private StrategyParams getStrategyParams() {
        // 获取策略默认值
        StrategyParams strategyParams = new StrategyParams(0.5, 5, 50);
        ScoreStrategy strategy = strategyMapper.getSelectedStrategy();
        if (strategy != null) {
            try {
                String params = strategy.getParams();
                String[] split = params.split("\\|");
                strategyParams = new StrategyParams(Double.parseDouble(split[0].trim()), Integer.parseInt(split[1].trim()), Integer.parseInt(split[2].trim()));
            } catch (Exception e) {
                log.warn("策略参数解析异常,使用默认参数:{}", strategyParams);
            }
        }
        return strategyParams;
    }


    @Deprecated
    private Double handleScore(Double nowPrice, List<DailyItem> priceList, List<DailyItem> rateList, StrategyParams params) {
        if (priceList != null && priceList.size() > 0) {
            Double prePrice = priceList.get(priceList.size() - 1).getItem();
            double increaseRate = (nowPrice - prePrice) / prePrice;
            priceList.add(new DailyItem(DateUtils.dateFormat.format(new Date()), nowPrice));
            rateList.add(new DailyItem(DateUtils.dateFormat.format(new Date()), increaseRate));
            List<Double> prices = priceList.stream().map(DailyItem::getItem).collect(Collectors.toList());
            List<Double> rates = rateList.stream().map(DailyItem::getItem).collect(Collectors.toList());
            return getScoreByList(prices, rates, params);
        }
        return 0.0;
    }

    // 根据预测价格来计算购买优先级
    private Double handleScore(StockInfo stockInfo) {
        // 获取该股票今预测价格
        LambdaQueryWrapper<PredictPrice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PredictPrice::getStockCode, stockInfo.getCode());
        queryWrapper.eq(PredictPrice::getDate, DateUtils.format1(new Date()));
        PredictPrice predictPrice = predictPriceMapper.selectOne(queryWrapper);
        Double predictPrice1 = predictPrice.getPredictPrice1();
        Double predictPrice2 = predictPrice.getPredictPrice2();
        // 以预测价格的平均住计算价格增长率
        double predictAvg = (predictPrice1 + predictPrice2) / 2;
        double increasePercent = predictAvg / stockInfo.getPrice();
        return increasePercent * 100;
    }


    @SneakyThrows
    public void saveDate(List<StockInfo> dataList) {
        log.info("开始更新数据库...");
        if (CollectionUtils.isNotEmpty(dataList)) {
            // 多线程批量更新数据库
            int pages = dataList.size() / UPDATE_BATCH_SIZE + 1;
            CountDownLatch countDownLatch = new CountDownLatch(pages);
            log.info("共{}条数据, 分{}页进行批量更新,每页{}条数据", dataList.size(), pages, UPDATE_BATCH_SIZE);
            for (int i = 1; i <= pages; i++) {
                int finalI = i;
                threadPool.submit(() -> {
                    try {
                        long startIndex = (long) (finalI - 1) * UPDATE_BATCH_SIZE;
                        final List<StockInfo> saveList = dataList.stream().skip(startIndex).limit(UPDATE_BATCH_SIZE).collect(Collectors.toList());
                        stockInfoService.saveOrUpdateBatch(saveList);
                        long endIndex = startIndex + UPDATE_BATCH_SIZE;
                        endIndex = Math.min(endIndex, dataList.size());
                        log.info("第{}个数据更新任务处理完成,任务更新范围[{},{}]内,共{}条数", pages - countDownLatch.getCount() + 1, startIndex + 1, endIndex, endIndex - startIndex);
                    } catch (Exception e) {
                        e.printStackTrace();
                        log.error("保存数据异常:{}", e.getMessage());
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }
            countDownLatch.await();
            log.info("数据库更新完成...");
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
        final JSONArray results = requestUtils.request3(buildParams(paramMap));
        final ArrayList<DailyItem> prices = new ArrayList<>();
//        int startIndex = results.size() >= HISTORY_PRICE_LIMIT ? results.size() - HISTORY_PRICE_LIMIT : 0;
        for (int i = 0; i < results.size(); i++) {
            final String s = results.getString(i);
            final String[] split = s.split(",");
            final String date = split[0].replaceAll("\\[", "");
            final String price1 = split[1];
            final String price2 = split[2];
            final String price3 = split[3];
            final String price4 = split[4].replaceAll("]", "");
            DailyItem dailyItem = new DailyItem();
            dailyItem.setDate(date);
            dailyItem.setItem(Double.parseDouble(price1) / 100);
            dailyItem.setPrice1(Double.parseDouble(price1) / 100);
            dailyItem.setPrice2(Double.parseDouble(price2) / 100);
            dailyItem.setPrice3(Double.parseDouble(price3) / 100);
            dailyItem.setPrice4(Double.parseDouble(price4) / 100);
            prices.add(dailyItem);
        }
        return prices;
    }

    // 获取最近一个月的成交订单订单
    public List<OrderInfo> getLastOrder() {
        final long timeMillis = System.currentTimeMillis();
        final String token = getToken();
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        final String end = DateUtils.dateFormat.format(calendar.getTime());
        calendar.add(Calendar.MONTH, -1);
        final String start = DateUtils.dateFormat.format(calendar.getTime());
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 115);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", 500);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        final JSONArray results = requestUtils.request2(buildParams(paramMap));
        return arrayToOrderList(results, false);
    }

    // 获取今日成交订单
    public List<OrderInfo> getTodayOrder() {
        final long timeMillis = System.currentTimeMillis();
        final String token = getToken();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 114);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", 100);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        final JSONArray results = requestUtils.request2(buildParams(paramMap));
        return arrayToOrderList(results, true);
    }

    private List<OrderInfo> arrayToOrderList(JSONArray results, boolean isToday) {
        final ArrayList<OrderInfo> orderList = new ArrayList<>();
        if (results == null || results.size() <= 1) {
            return orderList;
        }
        for (int i = 1; i < results.size(); i++) {
            final String result = results.getString(i);
            final String[] split = result.split("\\|");
            final String name = isToday ? split[0] : split[5];
            final String type = isToday ? split[1] : split[6];
            final String number = isToday ? split[2] : split[8];
            final String price = isToday ? split[3] : split[7];
            final String code = isToday ? split[5] : split[4];
            final String answerNo = isToday ? split[6] : split[1];
            final String date = isToday ? split[10] : split[0];
            final String time = split[11];
            if ("0".equals(answerNo) || "799999".equals(code)) {
                // 非买卖信息,跳过处理
                continue;
            }
            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setAnswerNo(answerNo);
            orderInfo.setCode(code);
            orderInfo.setName(name);
            orderInfo.setDate(date);
            orderInfo.setTime(time);
            orderInfo.setType(type);
            orderInfo.setPrice(Double.parseDouble(price));
            orderInfo.setNumber(Double.parseDouble(number));
            orderList.add(orderInfo);
        }
        return orderList;
    }

    // 计算价格日增长率曲线
    public List<DailyItem> getRateList(List<DailyItem> prices) {
        final ArrayList<DailyItem> rateList = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            final Double price = prices.get(i).getItem();
            final Double oldPrice = prices.get(i - 1).getItem();
            final double dailyIncreaseRate = oldPrice == 0 ? 0 : (price - oldPrice) / oldPrice;
            // 增长率乘100方便计算,否则数值太小
            double increaseRate = Double.parseDouble(String.format("%.4f", dailyIncreaseRate * 100));
            rateList.add(new DailyItem(prices.get(i).getDate(), increaseRate));
        }
        return rateList;
    }

    // 获取最近10个交易日天的日增长率,用以计算增长的稳定性和增长率总和
    // 价格稳定性用方差来衡量,增长率总和体现增长幅度
    // 增长率总和,乘以占比系数(index/size),离当前时间越近,趋近于1,离当前时间越远,系数趋近于0
    // 增长的天数越多,日增长率总和越大,价格方差越小,增长波动越小,代表稳定增长,评分越高
    public Double getScoreByList(List<Double> priceList, List<Double> rateList, StrategyParams params) {
        if (priceList.isEmpty() || rateList.isEmpty()) {
            return 0.0;
        }
        Double preRateFactor = params.getPreRateFactor();
        Integer priceTolerance = params.getPriceTolerance();
        Integer historyLimit = params.getHistoryLimit();
        List<Double> limitPriceList = priceList.stream().skip(priceList.size() > historyLimit ? priceList.size() - historyLimit : 0).limit(historyLimit).collect(Collectors.toList());
        List<Double> limitRateList = rateList.stream().skip(rateList.size() > historyLimit ? rateList.size() - historyLimit : 0).limit(historyLimit).collect(Collectors.toList());
        // 计算日增长率平均值总和
        double sumRate = 0;
        final int size = limitRateList.size();
        for (int i = 1; i <= size; i++) {
            // 修改前期数据对得分的影响,保证系数范围在[0.5,1]之间
            // 数值越小,前期数据对得分影响越低
            // 有常数阶和线性阶两种,可供调试
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
        // 方差范围限制,确保价格波动系数不为负数
        // 方差越趋向于0,价格波动系数越趋向于1,说明价格越稳定,得分越高
        // 方越趋向于 PRICE_BASE_NUMBER(价格波动容忍度),
        // 价格波动系数越趋向于0,说明价格波动越大,得分越低
        sqrt = Math.min(sqrt, priceTolerance);
        return sumRate * (1 - sqrt / priceTolerance);
    }

    // 计算交易日期差
    public int diffDate(Date date1, Date date2) {
        HashMap<String, Integer> dateMap = getDateMap();
        Integer from = dateMap.get(DateUtils.dateFormat.format(date1));
        Integer to = dateMap.get(DateUtils.dateFormat.format(date2));
        return Math.abs(to - from);
    }

    // 计算手续费,万五,最低五元
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


    @SneakyThrows
    public void writeHistoryPriceDataToCSV(String stockCode) {
        final StockInfo stockInfo = stockInfoService.getOne(new QueryWrapper<StockInfo>().lambda().eq(StockInfo::getCode, stockCode));
        List<DailyItem> historyPrices = getHistoryPrices(stockInfo.getCode());
        String filePath = new File("data/history_price_" + stockCode + ".csv").getAbsolutePath();
        if (profile.equalsIgnoreCase("prod")) {
            filePath = new File("/root/history_price_" + stockCode + ".csv").getAbsolutePath();
        }
        File file = new File(filePath);
        final BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
//        String csvHead = "date,code,price1,price2,price3,price4";
        String csvHead = "date,code,price1,price2,price3,price4,volume";
        bufferedWriter.write(csvHead);
        bufferedWriter.newLine();
        for (DailyItem item : historyPrices) {
            bufferedWriter.write(item.getDate().concat(","));
            bufferedWriter.write(stockCode.concat(","));
            bufferedWriter.write(item.getPrice1().toString().concat(","));
            bufferedWriter.write(item.getPrice2().toString().concat(","));
            bufferedWriter.write(item.getPrice3().toString().concat(","));
            bufferedWriter.write(item.getPrice4().toString().concat(","));
            bufferedWriter.write("0");
            bufferedWriter.newLine();
            bufferedWriter.flush();
//            bufferedWriter.write(item.getDate().concat(",").concat(item.getPrice1().toString()));
//            bufferedWriter.newLine();
//            bufferedWriter.write(item.getDate().concat(",").concat(item.getPrice2().toString()));
//            bufferedWriter.newLine();
//            bufferedWriter.write(item.getDate().concat(",").concat(item.getPrice3().toString()));
//            bufferedWriter.newLine();
//            bufferedWriter.write(item.getDate().concat(",").concat(item.getPrice4().toString()));
//            bufferedWriter.newLine();
        }
        bufferedWriter.close();
        log.info("股票:{}-{}, 历史数据保存完成！", stockInfo.getName(), stockInfo.getCode());
    }


    @SneakyThrows
    public void writeHistoryPriceDataToMongoDB(List<StockInfo> stockInfoList) {
        stockInfoList.forEach(s -> {
            List<DailyItem> historyPrices = getHistoryPrices(s.getCode());
            List<StockHistoryPrice> stockHistoryPriceList = historyPrices.stream().map(item -> {
                StockHistoryPrice stockHistoryPrice = new StockHistoryPrice();
                stockHistoryPrice.setName(s.getName());
                stockHistoryPrice.setCode(s.getCode());
                stockHistoryPrice.setDate(item.getDate());
                stockHistoryPrice.setPrice1(item.getPrice1());
                stockHistoryPrice.setPrice2(item.getPrice2());
                stockHistoryPrice.setPrice3(item.getPrice3());
                stockHistoryPrice.setPrice4(item.getPrice4());
                return stockHistoryPrice;
            }).collect(Collectors.toList());
            String collectionName = COLLECTION_NAME_PREFIX + s.getCode();
            mongoTemplate.insert(stockHistoryPriceList, collectionName);
            log.info("当前股票：{}-{},所有历史数据，初始化完成", s.getName(), s.getCode());
        });
    }


    // 首次初始化执行，写入4000支股票，每只股票约500条数据
//    @Scheduled(fixedDelay = Long.MAX_VALUE)
    public void updateLastHistoryPriceALL() {
        LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StockInfo::getDeleted, 1);
        List<StockInfo> stockInfoList = stockInfoService.list(queryWrapper);
        log.info("共需写入{}支股票历史数据", stockInfoList.size());
        writeHistoryPriceDataToMongoDB(stockInfoList);
        log.info("所有数据初始化完成，共{}只股票数据", stockInfoList.size());
    }

}
