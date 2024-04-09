package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.*;
import online.mwang.stockTrading.web.service.AccountInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
public class RunCleanJob extends BaseJob {

    private final AccountInfoService accountInfoService;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run() {
        cleanAccountInfo();
        cleanHistoryPrice();
    }

    private void cleanAccountInfo() {
        // 移除AccountInfo中一半的历史数据
        final List<AccountInfo> list = accountInfoService.list();
        final List<AccountInfo> deleteList = list.stream().skip(list.size() >> 1).collect(Collectors.toList());
        accountInfoService.removeBatchByIds(deleteList);
        log.info("共清理{}条账户信息历史数据。", deleteList.size());
    }

    private void cleanHistoryPrice() {
        // 移除MongoDB中前几个的历史数据的预测价格历史数据
        // 只保留最新的三个月数据
        final Set<String> collectionNames = mongoTemplate.getCollectionNames();
        AtomicInteger count = new AtomicInteger();
        collectionNames.stream().filter(s -> s.startsWith("predict")).forEach(c -> {
            final Query query = new Query(Criteria.where("date").lt(getPreMonthDate()));
            final List<Object> removed = mongoTemplate.findAllAndRemove(query, c);
            count.addAndGet(removed.size());
        });
        log.info("共清理{}条价格预测历史数据。", count);
    }

    private String getPreMonthDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MONTH, -3);
        return DateUtils.dateFormat.format(calendar.getTime());
    }
}
