package online.mwang.stockTrading.web.job;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.TradingRecord;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunSaleJob extends BaseJob {

    private final AllJobs jobs;
    private final TradingRecordService tradingRecordService;
    private final StockInfoService stockInfoService;

    public static final int SOLD_RETRY_TIMES = 4;

    @Override
    public void run(String runningId) {
        log.info("开始执行卖出任务====================================");
        sale(runningId);
        log.info("卖出任务执行完毕====================================");
    }


    public void sale(String runningId) {
        int time = 0;
        while (time++ < SOLD_RETRY_TIMES) {
            log.info("第{}次尝试卖出股票---------", time);
            if (jobs.checkSoldToday("1")) {
                log.info("今天已经有卖出记录了,无需重复卖出!");
                return;
            }
            // 撤销未成功订单
            if (!jobs.waitOrderCancel()) {
                log.info("存在未撤销失败订单,取消卖出任务！");
                return;
            }
            // 卖出最高收益的股票
            TradingRecord best = jobs.getBestRecord();
            if (best == null) {
                log.info("当前无可卖出股票,取消卖出任务！");
                return;
            }
            log.info("最佳卖出股票[{}-{}],买入价格:{},当前价格:{},预期收益:{},日收益率:{}", best.getCode(), best.getName(), best.getBuyPrice(), best.getSalePrice(), best.getIncome(), String.format("%.4f", best.getDailyIncomeRate()));
            // 等待最佳卖出时机
            if (jobs.enableSaleWaiting) {
                best = jobs.waitingBestRecord(best, runningId);
                if (best == null) {
                    log.info("不在交易时间段内，取消卖出任务！");
                    return;
                }
            }
            // 返回合同编号
            JSONObject res = jobs.buySale("S", best.getCode(), best.getSalePrice(), best.getBuyNumber());
            String saleNo = res.getString("ANSWERNO");
            if (saleNo == null) {
                log.info("当前股票[{}-{}]卖出失败,尝试卖出下一组。", best.getCode(), best.getName());
                continue;
            }
            // 查询卖出结果
            final Boolean success = jobs.waitOrderCancel(saleNo);
            if (success == null) {
                log.info("当前股票[{}-{}]撤销订单失败,取消卖出任务！", best.getCode(), best.getName());
                return;
            }
            if (!success) {
                log.info("当前股票[{}-{}]卖出失败,尝试再次卖出。", best.getCode(), best.getName());
//                enableSaleWaiting = false;
                continue;
            }
            best.setSold("1");
            best.setSaleNo(saleNo);
            final Date now = new Date();
            best.setSaleDate(now);
            best.setSaleDateString(DateUtils.dateFormat.format(now));
            best.setUpdateTime(now);
            tradingRecordService.updateById(best);
            // 更新账户资金
            jobs.getAvaliableAmount();
            // 增加股票交易次数
            StockInfo stockInfo = stockInfoService.getOne(new QueryWrapper<StockInfo>().lambda().eq(StockInfo::getCode, best.getCode()));
            stockInfo.setBuySaleCount(stockInfo.getBuySaleCount() + 1);
            stockInfoService.updateById(stockInfo);
            log.info("成功卖出股票[{}-{}], 卖出金额为:{}, 收益为:{},日收益率为:{}。", best.getCode(), best.getName(), best.getSaleAmount(), best.getIncome(), best.getDailyIncomeRate());
//            enableSaleWaiting = true;
            return;
        }
    }
}
