package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockTestPrice;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

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
            final Query query = new Query(Criteria.where("code").is(stockCode)).with(Sort.by(Sort.Direction.ASC, "date"));
            List<StockHistoryPrice> stockHistoryPrices = mongoTemplate.find(query, StockHistoryPrice.class);
            log.info("股票[{}-{}],训练数据集大小为:{}", s.getName(), s.getCode(), stockHistoryPrices.size());
            List<StockTestPrice> stockTestPrices = modelService.modelTrain(stockHistoryPrices, stockCode);
            final Query deleteQuery = new Query(Criteria.where("code").is(s.getCode()));
            final List<StockTestPrice> remove = mongoTemplate.findAllAndRemove(deleteQuery, StockTestPrice.class);
            log.info("股票[{}-{}],清除{}条废弃测试集数据", s.getName(), stockCode, remove.size());
            mongoTemplate.insert(stockTestPrices, StockTestPrice.class);
            log.info("股票[{}-{}],新写入{}条测试集数据", s.getName(), stockCode, stockTestPrices.size());
            long end = System.currentTimeMillis();
            log.info("股票[{}-{}],模型训练完成，共耗时:{}", s.getName(), stockCode, DateUtils.timeConvertor(end - start));
        });
    }
}
