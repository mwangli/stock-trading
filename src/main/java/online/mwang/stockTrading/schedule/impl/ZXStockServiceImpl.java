package online.mwang.stockTrading.schedule.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.dto.DailyItem;
import online.mwang.stockTrading.web.bean.dto.OrderStatus;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import online.mwang.stockTrading.web.mapper.ModelInfoMapper;
import online.mwang.stockTrading.web.mapper.StockInfoMapper;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import online.mwang.stockTrading.web.utils.OcrUtils;
import online.mwang.stockTrading.web.utils.RequestUtils;
import online.mwang.stockTrading.web.utils.SleepUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 10:32
 * @description: AllJobs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZXStockServiceImpl implements IStockService {

    public static final String TOKEN = "requestToken";
    public static final int LOGIN_RETRY_TIMES = 10;
    public static final int TOKEN_EXPIRE_MINUTES = 30;
    public final RequestUtils requestUtils;
    public final OcrUtils ocrUtils;
    public final StockInfoService stockInfoService;
    public final TradingRecordService tradingRecordService;
    public final AccountInfoMapper accountInfoMapper;
    public final StringRedisTemplate redisTemplate;
    public final MongoTemplate mongoTemplate;
    public final StockInfoMapper stockInfoMapper;
    public final ModelInfoMapper strategyMapper;
    public final SleepUtils sleepUtils;

    private HashMap<String, Object> buildParams(HashMap<String, Object> paramMap) {
        if (paramMap == null) return new HashMap<>();
        paramMap.put("cfrom", "H5");
        paramMap.put("tfrom", "PC");
        paramMap.put("newindex", "1");
        paramMap.put("MobileCode", "13278828091");
        paramMap.put("intacttoserver", "@ClZvbHVtZUluZm8JAAAANTI1QS00Qjc4");
        paramMap.put("reqno", System.currentTimeMillis());
        return paramMap;
    }

    public void clearToken() {
        redisTemplate.opsForValue().getAndDelete(TOKEN);
    }

    public String getToken() {
        final String token = redisTemplate.opsForValue().get(TOKEN);
        if (token == null) tryLogin();
        return redisTemplate.opsForValue().get(TOKEN);
    }

    public void setToken(String token) {
        if (token == null) return;
        redisTemplate.opsForValue().set(TOKEN, token, TOKEN_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }

    @SneakyThrows
    private List<String> getCheckCode() {
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", "41092");
        final JSONObject res = requestUtils.request(buildParams(paramMap));
        final String checkToken = res.getString("CHECKTOKEN");
        final String checkMessage = res.getString("MESSAGE");
        final String checkCode = parseCheckCode(checkMessage);
        return Arrays.asList(checkCode, checkToken);
    }

    @SneakyThrows
    private String parseCheckCode(String message) {
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
    private void tryLogin() {
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

    private Boolean doLogin() {
        String accountPassword = redisTemplate.opsForValue().get("ENCODE_ACCOUNT_PASSWORD");
        final List<String> checkCode = getCheckCode();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", "100");
        paramMap.put("modulus_id", "2");
        paramMap.put("accounttype", "ZJACCOUNT");
        paramMap.put("account", "880008900626");
        paramMap.put("MobileCode", "13278828091");
        paramMap.put("password", accountPassword);
        paramMap.put("signkey", "51cfce1626c7cb087b940a0c224f2caa");
        paramMap.put("CheckCode", checkCode.get(0));
        paramMap.put("CheckToken", checkCode.get(1));
        final JSONObject result = requestUtils.request(buildParams(paramMap));
        final String errorNo = result.getString("ERRORNO");
        if ("331100".equals(errorNo)) {
//            setToken(result.getString("TOKEN"));
            return true;
        }
        if ("-330203".equals(errorNo)) {
            // 验证码错误,继续尝试登录
            return false;
        }
        // 其他错误直接返回
        return null;
    }

    private List<OrderStatus> arrayToList(JSONArray result) {
        // 委托日期|时间|证券代码|证券|委托类别|买卖方向|状态|委托|数量|委托编号|均价|成交|股东代码|交易类别|
        ArrayList<OrderStatus> statusList = new ArrayList<>();
        if (result != null && result.size() > 1) {
            for (int i = 1; i < result.size(); i++) {
                String string = result.getString(i);
                String[] split = string.split("\\|");
                String code = split[0];
                String name = split[1];
                String status = split[2];
                String answerNo = split[8];
                OrderStatus orderStatus = new OrderStatus(answerNo, code, name, status);
                statusList.add(orderStatus);
            }
        }
        return statusList;
    }

    private String queryOrderStatus(String answerNo) {
        List<OrderStatus> orderInfos = listTodayAllOrder();
        log.info("查询到订单状态信息:");
        orderInfos.forEach(o -> log.info("{}-{}:{}", o.getCode(), o.getName(), o.getStatus()));
        Optional<OrderStatus> status = orderInfos.stream().filter(o -> o.getAnswerNo().equals(answerNo)).findFirst();
        if (!status.isPresent()) {
            log.info("未查询到合同编号为{}的订单交易状态！", answerNo);
            return null;
        }
        return status.get().getStatus();
    }

    private void cancelOrder(String answerNo) {
        String token = getToken();
        final long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", "111");
        paramMap.put("ContactID", answerNo);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        requestUtils.request(buildParams(paramMap));
    }

    @Override
    public Integer cancelAllOrder() {
        List<OrderInfo> orderInfos = getTodayOrder();
        orderInfos.forEach(o -> cancelOrder(o.getAnswerNo()));
        log.info("共取消{}条无效订单!", orderInfos.size());
        return orderInfos.size();
    }

    @Override
    public AccountInfo getAccountInfo() {
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
        accountInfo.setCreateTime(new Date());
        accountInfo.setUpdateTime(new Date());
        return accountInfo;
    }

    // 获取今日所有提交的委托订单，包含撤销和取消订单
    public List<OrderStatus> listTodayAllOrder() {
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
        return arrayToList(result);
    }

    @Override
    public Double getNowPrice(String code) {
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

    @Override
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
        return requestUtils.request(buildParams(paramMap));
    }


    @Override
    public boolean waitSuccess(String answerNo) {
        int times = 0;
        while (times++ < 10) {
            sleepUtils.second(30);
            final String status = queryOrderStatus(answerNo);
            if (status == null) {
                log.info("当前合同编号:{},订单状态查询失败。", answerNo);
                return false;
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
        return false;
    }

    // 获取每日最新股票数据
    @Override
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
            final double increasePercent = Double.parseDouble(increase);
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
            // 填充字段
            stockInfos.add(stockInfo);
        }
        log.info("共获取到{}条新数据。", stockInfos.size());
        return stockInfos;
    }

    // 获取历史价格曲线
    @Override
    public List<DailyItem> getHistoryPrices(String code) {
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
        for (int i = 0; i < results.size(); i++) {
            DailyItem dailyItem = new DailyItem();
            String s = results.getString(i);
            s = s.replaceAll("\\[", "").replaceAll("]", "");
            final String[] split = s.split(",");
            final String price1 = split[1];
            final String price2 = split[2];
            dailyItem.setDate(split[0]);
            dailyItem.setPrice1(Double.parseDouble(price1) / 100);
            dailyItem.setPrice2(Double.parseDouble(price2) / 100);
            if (split.length > 3) {
                final String price3 = split[3];
                final String price4 = split[4];
                dailyItem.setPrice3(Double.parseDouble(price3) / 100);
                dailyItem.setPrice4(Double.parseDouble(price4) / 100);
            }
            prices.add(dailyItem);
        }
        return prices;
    }

    // 获取历史订单
    public List<OrderInfo> getHistoryOrder(String begin, String end) {
        final String token = getToken();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 115);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", 500);
        paramMap.put("token", token);
        paramMap.put("BeginDate", begin);
        paramMap.put("EndDate", end);
        final JSONArray results = requestUtils.request2(buildParams(paramMap));
        return arrayToOrderList(results, false);
    }

    @Override
    // 获取历史订单 平台限制最大只能获取一个月跨度
    public List<OrderInfo> getHistoryOrder() {
        // 获取自2023年后历史订
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, Calendar.JANUARY, 1);
        int years = 3;
        final ArrayList<OrderInfo> orderInfos = new ArrayList<>();
        for (int i = 0; i < years * 12; i++) {
            if (calendar.getTime().getTime() > new Date().getTime()) break;
            String startDate = DateUtils.format1(calendar.getTime());
            calendar.add(Calendar.MONTH, 1);
            String endDate = DateUtils.format1(calendar.getTime());
            orderInfos.addAll(getHistoryOrder(startDate, endDate));
            // 避免请求过快触发安全警告
            sleepUtils.milliseconds(200);
        }
        return orderInfos;
    }

    // 获取今日成交订单
    @Override
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
        if (results == null || results.size() <= 1) return orderList;
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
            orderInfo.setNumber(Math.abs(Double.parseDouble(number)));
            orderList.add(orderInfo);
        }
        return orderList;
    }

    // 计算手续费,万分之五,最低五元
    @Override
    public Double getPeeAmount(Double amount) {
        return Math.max(5, amount * 0.0005);
    }
}