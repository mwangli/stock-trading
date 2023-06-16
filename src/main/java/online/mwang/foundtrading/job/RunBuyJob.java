package online.mwang.foundtrading.job;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.po.AccountInfo;
import online.mwang.foundtrading.bean.po.ScoreStrategy;
import online.mwang.foundtrading.bean.po.StockInfo;
import online.mwang.foundtrading.bean.po.TradingRecord;
import online.mwang.foundtrading.service.TradingRecordService;
import online.mwang.foundtrading.utils.DateUtils;
import online.mwang.foundtrading.utils.SleepUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunBuyJob extends BaseJob {

    public static final int BUY_RETRY_TIMES = 3;
    public static final int MAX_HOLD_STOCKS = 9;
    public static final int WAIT_TIME_SECONDS = 10;
    public static final int CANCEL_WAIT_TIMES = 10;
    public static final int BUY_RETRY_LIMIT = 50;
    public static final int MIN_HOLD_NUMBER = 100;
    public static final int MAX_HOLD_NUMBER = 100;
    public static final double LOW_PRICE_PERCENT = 0.85;
    public static final double LOW_PRICE_LIMIT = 5.0;
    private final DailyJob job;
    private final TradingRecordService tradingRecordService;

    @Override
    public void run() {
        int time = 0;
        while (!interrupted && time <= BUY_RETRY_TIMES) {
            time++;
            log.info("第{}次尝试买入股票---------", time);
            // 查询持仓股票数量
            final long holdCount = tradingRecordService.list(new LambdaQueryWrapper<TradingRecord>().eq(TradingRecord::getSold, "0")).size();
            final long needCount = MAX_HOLD_STOCKS - holdCount;
            if (needCount <= 0) {
                log.info("持仓股票数量已达到最大值:{}，无需购买!", MAX_HOLD_STOCKS);
                return;
            }
            // 撤销所有未成功订单，回收可用资金
//            if (!waitOrderStatus()) {
//                log.info("存在未撤销失败订单，取消购买任务！");
//                return;
//            }
            // 更新账户可用资金
            final AccountInfo accountInfo = job.updateAccountAmount();
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
            if (lowPrice < LOW_PRICE_LIMIT) {
                log.info("可用资金资金不足，取消购买任务！");
                return;
            }
            //  获取实时价格
            final List<StockInfo> dataList = job.getDataList();
            // 计算得分
            List<StockInfo> stockInfos = job.calculateScore(dataList, job.getStrategyParams());
            // 选择有交易权限合适价格区间的数据，按评分排序分组
            final List<StockInfo> limitList = stockInfos.stream()
                    .sorted(Comparator.comparingDouble(StockInfo::getScore).reversed())
                    .filter(s -> "1".equals(s.getPermission()) && s.getPrice() >= lowPrice && s.getPrice() <= highPrice)
                    .skip((long) time * BUY_RETRY_LIMIT).limit(BUY_RETRY_LIMIT).collect(Collectors.toList());
            if (limitList.size() < BUY_RETRY_LIMIT) {
                log.info("可买入股票不足{}条，取消购买任务！", BUY_RETRY_LIMIT);
                return;
            }
            // 在得分高的一组中随机选择一支买入
            StockInfo best = limitList.get(new Random(System.currentTimeMillis()).nextInt(BUY_RETRY_LIMIT));
            List<String> buyCodes = tradingRecordService.list().stream().filter(s -> "0".equals(s.getSold())).map(TradingRecord::getCode).collect(Collectors.toList());
            if (buyCodes.contains(best.getCode())) {
                log.info("当前股票[{}-{}]已经持有，尝试买入下一组股票", best.getCode(), best.getName());
                continue;
            }
            log.info("当前买入最佳股票[{}-{}],价格:{},评分:{}", best.getCode(), best.getName(), best.getPrice(), best.getScore());
            // 等待最佳买入时机
//            if (!waitingBestTime(best.getCode(), best.getName(), best.getPrice(), false)) {
//                log.info("未找到合适的买入时机，尝试买入下一组股票!");
//                continue;
//            }
            final int maxBuyNumber = (int) (availableAmount / best.getPrice());
            final int buyNumber = (maxBuyNumber / 100) * 100;
            JSONObject res = job.buySale("B", best.getCode(), best.getPrice(), (double) buyNumber);
            String buyNo = res.getString("ANSWERNO");
            if (buyNo == null) {
                log.info("当前股票[{}-{}]买入失败，尝试买入下一组股票!", best.getCode(), best.getName());
                continue;
            }
            // 查询买入结果
            final String status = waitOrderStatus(buyNo);
            if ("1".equals(status)) {
                // 买入成功后，保存交易数据
                final TradingRecord record = new TradingRecord();
                record.setCode(best.getCode());
                record.setName(best.getName());
                record.setBuyPrice(best.getPrice());
                record.setBuyNumber((double) buyNumber);
                record.setBuyNo(buyNo);
                final double amount = best.getPrice() * buyNumber;
                record.setBuyAmount(amount + job.getPeeAmount(amount));
                final Date now = new Date();
                record.setBuyDate(now);
                record.setBuyDateString(DateUtils.dateFormat.format(now));
                record.setSold("0");
                record.setCreateTime(now);
                record.setUpdateTime(now);
                // 保存选股策略ID
                final ScoreStrategy strategy = job.strategyMapper.getSelectedStrategy();
                record.setStrategyId(strategy == null ? 0 : strategy.getId());
                record.setStrategyName(strategy == null ? "默认策略" : strategy.getName());
                tradingRecordService.save(record);
                // 更新账户资金
                job.updateAccountAmount();
                // 更新交易次数
                stockInfos.stream().filter(s -> s.getCode().equals(best.getCode())).forEach(s -> s.setBuySaleCount(s.getBuySaleCount() + 1));
                log.info("成功买入股票[{}-{}], 买入价格:{}，买入数量:{}，买入金额:{}", record.getCode(), record.getName(), record.getBuyPrice(), record.getBuyNumber(), record.getBuyAmount());
                // 保存评分数据
                job.saveDate(stockInfos);
            }
            if ("0".equals(status)) {
                // 如果交易不成功，撤单后再次尝试卖出
                log.info("当前买入交易不成功，后尝试买入下一组股票。");
            }
            if ("-1".equals(status)) {
                log.info("当前订单撤销失败，取消购买任务!");
                return;
            }
        }
    }


    public String waitOrderStatus(String answerNo) {
        int times = 0;
        while (interrupted && times < CANCEL_WAIT_TIMES) {
            times++;
            SleepUtils.second(WAIT_TIME_SECONDS);
            final String status = job.queryOrderStatus(answerNo);
            if ("".equals(status)) {
                log.info("当前合同编号：{}，订单状态查询失败。", answerNo);
                return "-1";
            }
            if ("已成".equals(status)) {
                log.info("当前合同编号：{}，交易成功。", answerNo);
                return "1";
            }
            if ("已报".equals(status)) {
                log.info("当前合同编号：{}，交易不成功，进行撤单操作。", answerNo);
                job.cancelOrder(answerNo);
            }
            if ("已报待撤".equals(status)) {
                log.info("当前合同编号：{}，等待撤单完成...", answerNo);
            }
            if ("已撤".equals(status)) {
                log.info("当前合同编号：{}，订单撤销完成", answerNo);
                return "0";
            }
        }
        return "-1";
    }
}
