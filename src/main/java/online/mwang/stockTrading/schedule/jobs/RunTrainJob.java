package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.web.bean.po.PredictPrice;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunTrainJob extends BaseJob {

    private final IModelService modelService;
    private final MongoTemplate mongoTemplate;
    private final StockInfoService stockInfoService;


    @Override
    void run() {
        final LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StockInfo::getDeleted, "1");
        queryWrapper.eq(StockInfo::getPermission, "1");
        queryWrapper.ge(StockInfo::getPrice, 10);
        queryWrapper.le(StockInfo::getPrice, 20);
        final List<StockInfo> list = stockInfoService.list(queryWrapper);
        log.info("共获取{}条待训练股票.", list.size());
        list.forEach(s -> {
            log.info("股票[{}-{}],模型训练开始...", s.getName(), s.getCode());
            long start = System.currentTimeMillis();
            String stockCode = s.getCode();
            String collectionName = "historyPrices_" + stockCode;
            List<StockHistoryPrice> stockHistoryPrices = mongoTemplate.findAll(StockHistoryPrice.class, collectionName);
            final List<StockHistoryPrice> collect = stockHistoryPrices.stream().distinct().sorted(Comparator.comparing(StockHistoryPrice::getDate)).collect(Collectors.toList());
            log.info("股票[{}-{}],训练数据集大小为:{}", s.getName(), s.getCode(), collect.size());
            List<PredictPrice> predictPrices = modelService.modelTrain(collect, stockCode);

//            mongoTemplate.getCollectionNames().stream().filter(c->c.startsWith("testPrices_")).forEach(mongoTemplate::dropCollection);

            String testCollectionName = "testPrices_" + stockCode;
            List<Object> remove = mongoTemplate.findAllAndRemove(new Query(), testCollectionName);
            log.info("股票[{}-{}],清除{}条废弃测试集数据", s.getName(), stockCode, remove.size());
            mongoTemplate.insert(predictPrices, testCollectionName);
            log.info("股票[{}-{}],新写入{}条测试集数据", s.getName(), stockCode, predictPrices.size());
            long end = System.currentTimeMillis();
            log.info("股票[{}-{}],模型训练完成，共耗时:{}", s.getName(), stockCode, DateUtils.timeConvertor(end - start));
        });
    }
}
