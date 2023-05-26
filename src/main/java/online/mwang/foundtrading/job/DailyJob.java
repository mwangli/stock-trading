package online.mwang.foundtrading.job;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.po.AccountInfo;
import online.mwang.foundtrading.bean.po.DailyPrice;
import online.mwang.foundtrading.bean.po.FoundTradingRecord;
import online.mwang.foundtrading.bean.po.StockInfo;
import online.mwang.foundtrading.mapper.AccountInfoMapper;
import online.mwang.foundtrading.service.FoundTradingService;
import online.mwang.foundtrading.service.StockInfoService;
import online.mwang.foundtrading.utils.DateUtils;
import online.mwang.foundtrading.utils.RequestUtils;
import online.mwang.foundtrading.utils.SleepUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    private static final long BUY_RETRY_TIMES = 3;
    private static final long SOLD_RETRY_TIMES = 3;
    private static final long BUY_RETRY_LIMIT = 10;
    private static HashMap<String, Integer> dateMap;
    private final RequestUtils requestUtils;
    private final StockInfoService stockInfoService;
    private final FoundTradingService foundTradingService;
    private final AccountInfoMapper accountInfoMapper;
    private final StringRedisTemplate redisTemplate;

    // 每隔25分钟刷新Token
    @Scheduled(fixedRate = 1000 * 60 * 25, initialDelay = 1000 * 60 * 5)
    public void refreshToken() {
        cancelOrder("");
        log.info("刷新Token任务执行完毕！");
    }

    // 交易日开盘时间买入 9:30
    @Scheduled(cron = "0 0,15,30 9 ? * MON-FRI")
    public void runBuyJob() {
        log.info("开始执行买入任务====================================");
        buy(0);
        log.info("买入任务执行完毕====================================");
    }

    // 交易日收盘时间卖出 14:30
    @Scheduled(cron = "0 0 10 ? * MON-FRI")
    public void runSoldJob() {
        log.info("开始执行卖出任务====================================");
        sold(0);
        log.info("卖出任务执行完毕====================================");
    }

    // 更新账户余额，交易时间段内每小时执行一次
    @Scheduled(cron = "0 0 9-15 ? * MON-FRI")
    public void runAccountJob() {
        log.info("更新账户余额任务执行开始====================================");
        queryAccountAmount();
        log.info("更新账户余额任务执行结束====================================");
    }

    // 同步股票交易记录，每十天执行一次
    @Scheduled(cron = "0 0 15 1/10 * ?")
    public void runSyncJob() {
        log.info("同步订单任务执行开始====================================");
        syncBuySaleRecord();
        log.info("同步订单执行结束====================================");
    }

    // 更新股票交易权限，每半月月执行一次
    @Scheduled(cron = "0 0 12 1,15 * ?")
    public void runFlushJob() {
        log.info("更新权限任务执行开始====================================");
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

    public void flushPermission() {
        List<StockInfo> dataList = getDataList();
        dataList.forEach(info -> {
            String token = redisTemplate.opsForValue().get("requestToken");
            final long timeMillis = System.currentTimeMillis();
            HashMap<String, Object> paramMap = new HashMap<>();
            paramMap.put("action", "110");
            paramMap.put("PriceType", 0);
            paramMap.put("Direction", "B");
            paramMap.put("StockCode", info.getCode());
            paramMap.put("Price", info.getPrice());
            paramMap.put("Volume", 100);
            paramMap.put("reqno", timeMillis);
            paramMap.put("token", token);
            final JSONObject result = requestUtils.request3(buildParams(paramMap));
            final String newToken = result.getString("TOKEN");
            redisTemplate.opsForValue().set("requestToken", newToken);
            final String errorNo = result.getString("ERRORNO");
            final StockInfo stockInfo = new StockInfo();
            if ("-57".equals(errorNo)) {
                stockInfo.setPermission("1");
                log.info("修改当前股票[{}-{}]交易权限", info.getCode(), info.getName());
                stockInfoService.update(stockInfo, new UpdateWrapper<StockInfo>().lambda().eq(StockInfo::getCode, info.getCode()));
            }
        });
        log.info("刷新交易权限任务执行完毕！");
    }

    public void sold(int times) {
        if (times >= SOLD_RETRY_TIMES) {
            log.error("{}次尝试卖出股票失败，请检查程序代码！", times);
            return;
        }
        log.info("第{}次尝试卖出股票---------", times + 1);
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
        double maxRate = -100.00;
        FoundTradingRecord maxRateRecord = null;
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
            FoundTradingRecord selectRecord = foundTradingService.getOne(new QueryWrapper<FoundTradingRecord>().eq("code", code).eq("sold", "0"));
            if (selectRecord == null) {
                log.info("当前股票[{}-{}]未查询到买入记录,开始同步最近一个月订单", code, name);
                syncBuySaleRecord();
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
                    if (dailyIncomeRate > maxRate) {
                        selectRecord.setSalePrice(price);
                        selectRecord.setSaleNumber(selectRecord.getBuyAmount());
                        selectRecord.setSaleAmount(saleAmount);
                        selectRecord.setIncome(income);
                        selectRecord.setIncomeRate(incomeRate);
                        selectRecord.setHoldDays(dateDiff);
                        selectRecord.setDailyIncomeRate(incomeRate);
                        maxRateRecord = selectRecord;
                    }
                }
            }
        }
        // 卖出最高收益的股票
        if (maxRateRecord == null) {
            log.error("无可卖出股票，无法进行卖出交易！");
        } else {
            log.info("最佳卖出股票[{}-{}]，买入金额:{}，卖出金额:{}，收益:{}，日收益率:{}", maxRateRecord.getCode(), maxRateRecord.getName(), maxRateRecord.getBuyAmount(),
                    maxRateRecord.getSaleAmount(), maxRateRecord.getIncome(), String.format("%.4f", maxRateRecord.getDailyIncomeRate()));
            // 返回合同编号
            final String saleNo = buySale("S", maxRateRecord.getCode(), maxRateRecord.getSalePrice(), maxRateRecord.getBuyNumber());
            if (saleNo == null) {
                log.info("第{}次尝试卖出失败---------", times + 1);
                sold(times + 1);
            } else {
                // 等待一分钟后查询卖出结果
                log.info("等待20秒后查询卖出交易结果...");
                SleepUtils.second(20);
                if (queryStatus(saleNo)) {
                    maxRateRecord.setSold("1");
                    maxRateRecord.setSaleNo(saleNo);
                    final Date now = new Date();
                    maxRateRecord.setSaleDate(now);
                    maxRateRecord.setUpdateTime(now);
                    foundTradingService.updateById(maxRateRecord);
                    // 更新账户资金
                    queryAccountAmount();
                    log.info("成功卖出股票[{}-{}], 卖出金额为:{}, 收益为:{}，日收益率为:{}", maxRateRecord.getCode(), maxRateRecord.getName(),
                            maxRateRecord.getSaleAmount(), maxRateRecord.getIncome(), maxRateRecord.getDailyIncomeRate());
                } else {
                    // 如果交易不成功，撤单后重新计算卖出
                    log.info("卖出交易不成功，进行撤单操作");
                    cancelOrder(saleNo);
                    log.info("第{}次尝试卖出失败---------", times + 1);
                    sold(times + 1);
                }
            }
        }
    }

    public void buy(int times) {
        if (times >= BUY_RETRY_TIMES) {
            log.error("{}次尝试买入股票失败，请检查程序代码！", times);
            return;
        }
        log.info("第{}次尝试买入股票---------", times + 1);
        // 买入之前先去撤销所有未成功订单
        cancelAllOrder();
        //  更新每日数据
        final List<StockInfo> dataList = getUpdateData();
        // 查询账户可用资金
//        final AccountInfo accountInfo = queryAccountAmount();
        // 选择有交易权限合适价格区间的数据，按评分排序分组
        final List<StockInfo> filterList = dataList.stream()
                .filter(s -> "1".equals(s.getPermission()) && s.getPrice() >= 8 && s.getPrice() <= 12)
                .sorted(Comparator.comparing(StockInfo::getScore).reversed())
                .collect(Collectors.toList());
        // 在得分高的一组中随机选择一支买入
        List<StockInfo> limitList = filterList.stream().skip(times * BUY_RETRY_LIMIT).limit(BUY_RETRY_LIMIT).collect(Collectors.toList());
        List<String> buyCodes = foundTradingService.list().stream().filter(s -> "0".equals(s.getSold())).map(FoundTradingRecord::getCode).collect(Collectors.toList());
        StockInfo best = limitList.get(new Random().nextInt(10));
        if (buyCodes.contains(best.getCode())) {
            log.info("当前股票[{}-{}]已经持有，尝试买入下一股票", best.getCode(), best.getName());
            buy(times + 1);
        }
        log.info("尝试买入最佳股票[{}-{}],价格:{},评分:{}，日增长率曲线:{}", best.getCode(), best.getName(), best.getPrice(), best.getScore(), best.getIncreaseRate());
        String accountType = "SH".equals(best.getMarket()) ? "SHACCOUNT" : "SZACCOUNT";
        double buyNumber = 100.00;
        final String buyNo = buySale("B", best.getCode(), best.getPrice(), buyNumber);
        if (buyNo == null) {
            log.info("无法买入当前股票，尝试买入下一股票");
            buy(times + 1);
        } else {
            // 等待10秒后后查询买入结果
            log.info("等待10秒后查询买入结果...");
            SleepUtils.second(30);
            if (queryStatus(buyNo)) {
                final FoundTradingRecord record = new FoundTradingRecord();
                record.setCode(best.getCode());
                record.setName(best.getName());
                record.setBuyPrice(best.getPrice());
                record.setBuyNumber(buyNumber);
                record.setBuyNo(buyNo);
                final double amount = best.getPrice() * buyNumber;
                record.setBuyAmount(amount + getPeeAmount(amount));
                final Date now = new Date();
                record.setBuyDate(now);
                record.setSold("0");
                record.setCreateTime(now);
                record.setUpdateTime(now);
                foundTradingService.save(record);
                // 买入成功后，保存交易数据
                saveDate();
                // 更新账户资金
                queryAccountAmount();
                log.info("成功买入股票[{}-{}], 买入价格:{}，买入数量:{}，买入金额:{}", record.getCode(), record.getName(), record.getBuyPrice(), record.getBuyNumber(), record.getBuyAmount());
            } else {
                // 如果交易不成功，撤单后再次尝试卖出
                log.info("当前买入交易不成功，撤单后尝试买入下一股票");
                cancelOrder(buyNo);
                buy(times + 1);
            }
        }
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

    // 查询账户资金
    public AccountInfo queryAccountAmount() {
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

    public void cancelAllOrder() {
        String token = redisTemplate.opsForValue().get("requestToken");
        final long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", 152);
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", 20);
        paramMap.put("op_station", 4);
        paramMap.put("token", token);
        paramMap.put("reqno", timeMillis);
        JSONArray result = requestUtils.request2(buildParams(paramMap));
        for (int i = 1; i < result.size(); i++) {
            String string = result.getString(i);
            String[] split = string.split("\\|");
            String code = split[0];
            String name = split[1];
            String answerNo = split[8];
            log.info("撤销当前股票[{}-{}]未成功订单", code, name);
            cancelOrder(answerNo);
        }
    }

    public String buySale(String type, String code, Double price, Double number) {
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

        return result.getString("ANSWERNO");
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
        for (int i = 1; i <= 20; i++) {
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
                final Date now = new Date();
                final StockInfo stockInfo = new StockInfo();
                stockInfo.setName(name);
                stockInfo.setCode(code);
                stockInfo.setMarket(market);
                stockInfo.setPrice(price);
                stockInfo.setCreateTime(now);
                stockInfo.setUpdateTime(now);
                stockInfo.setPermission("0");
                stockInfos.add(stockInfo);
            }
        }
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
                final LambdaQueryWrapper<FoundTradingRecord> lambdaQueryWrapper = new QueryWrapper<FoundTradingRecord>().lambda()
                        .eq(FoundTradingRecord::getCode, code).like(FoundTradingRecord::getBuyNo, answerNo);
                final FoundTradingRecord selectedOrder = foundTradingService.getOne(lambdaQueryWrapper);
                if (selectedOrder == null) {
                    log.info("当前股票[{}-{}]买入记录不存在，新增买入记录", name, code);
                    // 合并多个买入订单
                    final LambdaQueryWrapper<FoundTradingRecord> queryWrapper = new QueryWrapper<FoundTradingRecord>().lambda()
                            .eq(FoundTradingRecord::getCode, code).eq(FoundTradingRecord::getSold, "0");
                    final FoundTradingRecord selectedRecord = foundTradingService.getOne(queryWrapper);
                    if (selectedRecord == null) {
                        final FoundTradingRecord record = new FoundTradingRecord();
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
                        foundTradingService.save(record);
                    } else {
                        selectedRecord.setBuyPrice(price);
                        selectedRecord.setBuyNumber(number + selectedRecord.getBuyNumber());
                        final double amount = price * number;
                        selectedRecord.setBuyAmount(selectedRecord.getBuyAmount() + amount + getPeeAmount(amount));
                        final Date buyDate = DateUtils.dateTimeFormat.parse(dateString);
                        selectedRecord.setBuyDate(buyDate);
                        selectedRecord.setBuyNo(selectedRecord.getBuyNo() + "," + answerNo);
                        final Date now = new Date();
                        selectedRecord.setUpdateTime(now);
                        foundTradingService.updateById(selectedRecord);
                    }

                }
            }
            if ("卖出".equals(type)) {
                // 查询卖出订单信息是否存在
                final LambdaQueryWrapper<FoundTradingRecord> lambdaQueryWrapper = new QueryWrapper<FoundTradingRecord>().lambda()
                        .eq(FoundTradingRecord::getCode, code).eq(FoundTradingRecord::getSaleNo, answerNo);
                final FoundTradingRecord selectedOrder = foundTradingService.getOne(lambdaQueryWrapper);
                if (selectedOrder == null) {
                    log.info("当前股票[{}-{}]卖出记录不存在，新增卖出记录", name, code);
                    final LambdaQueryWrapper<FoundTradingRecord> queryWrapper = new QueryWrapper<FoundTradingRecord>().lambda()
                            .eq(FoundTradingRecord::getCode, code).eq(FoundTradingRecord::getSold, "0");
                    final FoundTradingRecord record = foundTradingService.getOne(queryWrapper);
                    record.setSalePrice(price);
                    number = number < 0 ? -number : number;
                    record.setSaleNumber(number);
                    final double amount = price * number;
                    record.setSaleAmount(amount - getPeeAmount(amount));
                    final Date saleDate = DateUtils.dateTimeFormat.parse(dateString);
                    record.setSaleDate(saleDate);
                    record.setSold("1");
                    record.setSaleNo(answerNo);
                    final Date now = new Date();
                    record.setUpdateTime(now);
                    // 计算收益和日收益率
                    double income = record.getSaleAmount() - record.getBuyAmount();
                    record.setIncome(income);
                    int dateDiff = diffDate(record.getBuyDate(), record.getSaleDate());
                    record.setHoldDays(dateDiff);
                    double incomeRate = income / record.getBuyAmount() / dateDiff * 100;
                    record.setDailyIncomeRate(incomeRate);
                    foundTradingService.update(record, queryWrapper);
                }
            }
        }
    }

    @SneakyThrows
    public List<StockInfo> getUpdateData() {
        log.info("开始获取每日价格数据......");
        List<StockInfo> stockInfos = getDataList();
        log.info("共获取到{}条新数据。", stockInfos.size());
        // 获取已经存在的所有数据
        List<StockInfo> list = stockInfoService.list();
        List<StockInfo> updateData = mergeDataList(stockInfos, list);
        log.info("待更新{}条数据。", updateData.size());
        //  缓存更新后的数据，防止数据丢失
        redisTemplate.opsForValue().set("dataList", JSON.toJSONString(updateData), 8, TimeUnit.HOURS);
        return updateData;
    }

    @SneakyThrows
    public void saveDate() {
        log.info("开始更新数据库......");
        final String dataListString = redisTemplate.opsForValue().get("dataList");
        final List<StockInfo> dataList = JSON.parseArray(dataListString, StockInfo.class);
        if (dataList != null) {
            // 多线程写入数据库
            CountDownLatch countDownLatch = new CountDownLatch(5);
            final int pageSize = 1000;
            final int cores = Runtime.getRuntime().availableProcessors();
            for (int i = 0; i < cores; i++) {
                int finalI = i;
                new Thread(() -> {
                    List<StockInfo> saveList = dataList.stream().skip((long) finalI * pageSize).limit(pageSize).collect(Collectors.toList());
                    stockInfoService.saveOrUpdateBatch(saveList);
                    countDownLatch.countDown();
                }).start();
            }
            countDownLatch.await();
            log.info("数据库更新完成......");
        }
    }


    // 合并新老数据
    public List<StockInfo> mergeDataList(List<StockInfo> newInfos, List<StockInfo> stockInfos) {
        ArrayList<StockInfo> list = new ArrayList<>();
        newInfos.forEach(info -> {
            // 假设为新数据
            AtomicBoolean isNewData = new AtomicBoolean(true);
            // 数据已经存在则更新最新价格
            stockInfos.stream().filter(s -> s.getCode().equals(info.getCode())).findFirst().ifPresent(selectedInfo -> {
                if (selectedInfo.getPrices() == null || selectedInfo.getPrices().equals("[]")) {
                    List<DailyPrice> prices = getHistoryPrices(selectedInfo.getCode(), selectedInfo.getMarket());
                    selectedInfo.setPrices(JSON.toJSONString(prices));
                }
                List<DailyPrice> prices = JSONObject.parseArray(selectedInfo.getPrices(), DailyPrice.class);
                final String date = DateUtils.dateFormat.format(new Date());
                // 剔除今天的数据(一天内价格多次更新)
                List<DailyPrice> dailyPrices = prices.stream().filter(p -> !p.getDate().equals(date)).collect(Collectors.toList());
                final DailyPrice dailyPrice = new DailyPrice(date, info.getPrice());
                dailyPrices.add(dailyPrice);
                if (prices.size() > 11) {
                    prices.remove(0);
                }
                selectedInfo.setUpdateTime(new Date());
                selectedInfo.setPrice(info.getPrice());
                selectedInfo.setPrices(JSON.toJSONString(dailyPrices));
                StockInfo stockInfo = setIncreaseRateAndScore(selectedInfo, prices);
                list.add(stockInfo);
                isNewData.set(false);
            });
            // 数据不存在则获取历史价格
            if (isNewData.get()) {
                List<DailyPrice> prices = getHistoryPrices(info.getCode(), info.getMarket());
                info.setPrices(JSON.toJSONString(prices));
                StockInfo stockInfo = setIncreaseRateAndScore(info, prices);
                list.add(stockInfo);
            }
        });
        return list;
    }

    // 获取历史价格曲线
    public List<DailyPrice> getHistoryPrices(String code, String market) {
        // 获取历史数据
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("c.funcno", 20009);
        paramMap.put("c.version", 1);
        paramMap.put("c.stock_code", code);
        paramMap.put("c.market", market);
        paramMap.put("c.type", "day");
        paramMap.put("c.count", "20");
        paramMap.put("c.cfrom", "H5");
        paramMap.put("c.tfrom", "PC");
        final JSONArray results = requestUtils.request(buildParams(paramMap));
        // 解析数据
        final ArrayList<DailyPrice> prices = new ArrayList<>();
        if (results.size() >= 10) {
            for (int i = results.size() - 10; i < results.size(); i++) {
                final String s = results.getString(i);
                final String[] split = s.split(",");
                final String date = split[0].replaceAll("\\[", "");
                final String price1 = split[4];
                final double v = Double.parseDouble(price1);
                final DailyPrice dailyPrice = new DailyPrice(date, v / 100);
                prices.add(dailyPrice);
            }
        } else {
            log.warn("历史数据不足10条，丢弃该数据。");
        }
        return prices;
    }

    // 根据历史价格计算日增长率曲线和得分
    public StockInfo setIncreaseRateAndScore(StockInfo info, List<DailyPrice> prices) {
        final List<Double> rateList = getRateList(prices);
        info.setIncreaseRate(JSONObject.toJSONString(rateList));
        List<Double> priceList = prices.stream().map(DailyPrice::getPrice).collect(Collectors.toList());
        final Double score = getScore(priceList, rateList);
        info.setScore(score);
        return info;
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

    // 计算日增长率曲线
    public List<Double> getRateList(List<DailyPrice> prices) {
        final ArrayList<Double> rateList = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            final Double price = prices.get(i).getPrice();
            final Double oldPrice = prices.get(i - 1).getPrice();
            final double dailyIncreaseRate = oldPrice == 0 ? 0 : (price - oldPrice) / oldPrice;
            // 增长率乘100方便计算，否则数值太小
            rateList.add(Double.parseDouble(String.format("%.4f", dailyIncreaseRate * 100)));
        }
        return rateList;
    }

    // 获取最近10个交易日天的日增长率，用以计算增长的稳定性和增长率总和
    // 价格稳定性用方差来衡量,增长率总和体现增长幅度
    // 增长的天数越多，日增长率总和越大，价格方差越小，增长波动越小，代表稳定增长，评分越高
    public Double getScore(List<Double> priceList, List<Double> rateList) {
        if (priceList.isEmpty() || rateList.isEmpty()) {
            return 0.0;
        }
        // 计算日增长率平均值
        final double fixSum = rateList.stream().reduce(Double::sum).orElse(0.0);
        // 计算价格方差
        Double priceSum = priceList.stream().reduce(Double::sum).orElse(0.0);
        final double priceAvg = priceSum / priceList.size();
        double sum = 0;
        for (Double price : priceList) {
            sum += Math.pow((price - priceAvg), 2);
        }
        double sqrt = Math.sqrt(sum / priceList.size());
        sqrt = sqrt > 2 ? 2 : sqrt;
        return fixSum * (1 - sqrt / 2);
    }

    // 计算交易日期差
    public int diffDate(Date date1, Date date2) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
        HashMap<String, Integer> dateMap = getDateMap();
        Integer from = dateMap.get(simpleDateFormat.format(date1));
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
            for (int i = 0; i < split.length; i++) {
                dateMap.put(split[i], i);
            }
        }
        return dateMap;
    }
}
