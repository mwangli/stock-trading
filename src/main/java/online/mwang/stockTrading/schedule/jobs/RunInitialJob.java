package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.schedule.IDataService;
import online.mwang.stockTrading.web.bean.dto.DailyItem;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.TradingRecord;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import online.mwang.stockTrading.web.mapper.TradingRecordMapper;
import online.mwang.stockTrading.web.service.OrderInfoService;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.*;
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
public class RunInitialJob extends BaseJob {

    private final IDataService dataService;
    private final AccountInfoMapper accountInfoMapper;
    private final IModelService modelService;
    private final MongoTemplate mongoTemplate;
    private final StockInfoService stockInfoService;
    private final OrderInfoService orderInfoService;
    private final TradingRecordMapper tradingRecordMapper;
    private final TradingRecordService tradingRecordService;

    @Override
    public void run() {
        initHistoryPriceData();
        initHistoryOrder();
    }

    private void initHistoryOrder() {
        // 初始化订单数据，当交易记录数据丢失，或者在证券平台上已有订单数据，需要同步
        // 将数据写入到TradingRecord 和 OrderInfo表
        final List<OrderInfo> historyOrders = dataService.getHistoryOrder();
        final List<OrderInfo> todayOrders = dataService.getTodayOrder();
        historyOrders.addAll(todayOrders);
        final List<TradingRecord> tradingRecords = buildTradingRecord(historyOrders);
        // 写入时根据买入股票编号，交易日期来判断是否已经存在
        tradingRecords.forEach(this::fixProps);
        final List<TradingRecord> saveList = tradingRecords.stream().filter(r -> !isExist(r)).collect(Collectors.toList());
        tradingRecordService.saveBatch(saveList);
        log.info("共写入{}条完整交易记录。", saveList.size());
    }

    private boolean isExist(TradingRecord record) {
        final TradingRecord find = tradingRecordMapper.getByCodeAndDate(record.getCode(), record.getBuyDateString());
        return find != null;
    }

    private void fixProps(TradingRecord tradingRecord) {
        tradingRecord.setCreateTime(new Date());
        tradingRecord.setUpdateTime(new Date());
    }

    private List<TradingRecord> buildTradingRecord(List<OrderInfo> historyOrder) {
        // 将订单数据组装成交易数据，这个问题难点在于可能存在同一个股票的多笔交易订单
        // 可能买入一笔，然后分三笔卖出，如何将这4个订单数据组装成同一个交易记录中，后续可能还买入同一股
        // 利用队列结构来实现，维护一个不完整记录队列，出现卖出订单时，尝试去队列找买入记录进行填充(卖出订单之前必然有买入订单)
        // TODO
        final Queue<TradingRecord> unfinishedQueue = new LinkedList<>();
        final Queue<TradingRecord> finishedQueue = new LinkedList<>();
        historyOrder.forEach(orderInfo -> {
            if ("买入".equals(orderInfo.getType())) {

            }
            if ("卖出".equals(orderInfo.getType())) {

            }
        });
        log.info("共组装成{}条交易记录，剩余{}条未完成订单", finishedQueue.size(), unfinishedQueue.size());
        return new ArrayList<>(finishedQueue);
    }

    private void fixPropsFromBuyOrder(TradingRecord record, OrderInfo order) {
        record.setBuyNo(order.getCode());
        record.setBuyDateString(order.getDate());
        record.setBuyPrice(order.getPrice());
//        record.set(order.getPrice());
    }

    private void fixPropsFromSaleOrder(TradingRecord record, OrderInfo order) {

    }


    private void initHistoryPriceData() {
        // 首次初始化执行，写入4000支股票，每只股票约500条数据
        LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StockInfo::getDeleted, 1);
        List<StockInfo> stockInfoList = stockInfoService.list(queryWrapper);
        stockInfoList.forEach(s -> {
            List<DailyItem> historyPrices = dataService.getHistoryPrices(s.getCode());
            List<StockHistoryPrice> stockHistoryPrices = historyPrices.stream().map(item -> {
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
            String collectionName = "code_" + s.getCode();
            // 先判断是否有数据存在，防止误操作写入重复数据
            if (mongoTemplate.count(new Query(), collectionName) == 0) {
                mongoTemplate.insert(stockHistoryPrices, collectionName);
                log.info("股票[{}-{}]，历史数据写入完成！", s.getName(), s.getCode());
            }
        });
        log.info("共写入了{}支股票历史数据", stockInfoList.size());
    }
}
