package online.mwang.stockTrading.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.web.bean.base.Response;
import online.mwang.stockTrading.web.bean.dto.PointsDTO;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.bean.query.ModelInfoQuery;
import online.mwang.stockTrading.web.bean.query.StockInfoQuery;
import online.mwang.stockTrading.web.bean.vo.Point;
import online.mwang.stockTrading.web.service.ModelInfoService;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:56
 * @description: StrategyController
 */
@RestController
@RequestMapping("/modelInfo")
@RequiredArgsConstructor
public class ModelInfoController {

    private final static String ASCEND = "ascend";
    private final static String DESCEND = "descend";
    private final static String TEST_COLLECTION_NAME = "stockTestPrice";
    private final static String TRAIN_COLLECTION_NAME = "stockHistoryPrice";
    private final static String VALIDATE_COLLECTION_NAME = "stockPredictPrice";
    private final ModelInfoService modelInfoService;
    private final MongoTemplate mongoTemplate;


    @GetMapping("/list")
    public Response<List<ModelInfo>> list(ModelInfoQuery query) {
        LambdaQueryWrapper<ModelInfo> queryWrapper = new QueryWrapper<ModelInfo>().lambda()
                .like(ObjectUtils.isNotNull(query.getName()), ModelInfo::getName, query.getName())
                .eq((ObjectUtils.isNotNull(query.getStatus())), ModelInfo::getStatus, query.getStatus())
                .orderBy(true, ASCEND.equals(query.getSortOrder()), ModelInfo.getOrder(query.getSortKey()));
        Page<ModelInfo> pageResult = modelInfoService.page(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }

    @GetMapping("/listTestData")
    public Response<PointsDTO> listTestData(StockInfoQuery param, String collectionName) {
        String name = param.getName();
        String stockCode = name.split("_")[1].split("\\.")[0];
        // 查找测试集数据
        collectionName = collectionName == null ? TEST_COLLECTION_NAME : collectionName;
        final Query query = new Query(Criteria.where("code").is(stockCode).and("date").ne(null)).with(Sort.by(Sort.Direction.ASC, "date"));
        List<StockPrices> stockTestPrices = mongoTemplate.find(query, StockPrices.class, collectionName);
        String maxDate = stockTestPrices.stream().map(StockPrices::getDate).max(String::compareTo).orElse("");
        String minDate = stockTestPrices.stream().map(StockPrices::getDate).min(String::compareTo).orElse("");
        // 查找历史数据
        Query historyQuery = new Query(Criteria.where("code").is(stockCode).and("date").lte(maxDate).gte(minDate));
        List<StockPrices> stockHistoryPrices = mongoTemplate.find(historyQuery, StockPrices.class, TRAIN_COLLECTION_NAME);
        // 计算日增长率
        final ArrayList<Point> points = new ArrayList<>();
        for (int i = 1; i < stockHistoryPrices.size(); i++) {
            double todayPrice = stockHistoryPrices.get(i).getPrice1();
            double preDayPrice = stockHistoryPrices.get(i - 1).getPrice1();
            double increaseRate = preDayPrice == 0 ? 0 : (todayPrice - preDayPrice) / preDayPrice * 100;
            Point point = new Point(stockHistoryPrices.get(i).getDate(), Double.parseDouble(String.format("%.4f", increaseRate)));
            point.setType("实际日增长率");
            points.add(point);
        }
        for (StockPrices stockTestPrice : stockTestPrices) {
            Double increaseRate = stockTestPrice.getIncreaseRate();
            final Point point = new Point(stockTestPrice.getDate(),
                    Double.parseDouble(String.format("%.4f", increaseRate == null ? 0 : increaseRate)));
            point.setType("预测日增长率");
            points.add(point);
        }
        PointsDTO pointsDTO = new PointsDTO(points);
        return Response.success(pointsDTO);
    }

    @GetMapping("/listValidateData")
    public Response<PointsDTO> listValidateData(StockInfoQuery param) {
        return listTestData(param, VALIDATE_COLLECTION_NAME);
    }
}
