package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunTrainJob extends BaseJob {

    private final IModelService modelService;
    private final MongoTemplate mongoTemplate;


    @Override
    void run() {
        Set<String> collectionNames = mongoTemplate.getCollectionNames();
        List<StockHistoryPrice> historyPrices = new ArrayList<>();
        collectionNames.stream().filter(c -> c.startsWith("code_")).forEach(c -> historyPrices.addAll(mongoTemplate.findAll(StockHistoryPrice.class, c)));
        modelService.modelTrain(historyPrices);
    }
}
