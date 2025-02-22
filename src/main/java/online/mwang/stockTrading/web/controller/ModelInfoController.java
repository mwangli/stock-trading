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

import java.util.List;
import java.util.stream.Collectors;

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
                .like(ObjectUtils.isNotNull(query.getCode()), ModelInfo::getCode, query.getCode())
                .like(ObjectUtils.isNotNull(query.getName()), ModelInfo::getName, query.getName())
                .eq((ObjectUtils.isNotNull(query.getStatus())), ModelInfo::getStatus, query.getStatus())
                .orderBy(true, ASCEND.equals(query.getSortOrder()), ModelInfo.getOrder(query.getSortKey()));
        Page<ModelInfo> pageResult = modelInfoService.page(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }

    @GetMapping("/listTestData")
    public Response<PointsDTO> listTestData(StockInfoQuery param, String collectionName) {
        // 查找测试集数据
        String stockCode = param.getCode();
        collectionName = collectionName == null ? TEST_COLLECTION_NAME : collectionName;
        final Query query = new Query(Criteria.where("code").is(stockCode).and("date").ne(null)).with(Sort.by(Sort.Direction.ASC, "date"));
        List<StockPrices> stockTestPrices = mongoTemplate.find(query, StockPrices.class, collectionName);
        List<Point> pointList = stockTestPrices.stream().map(p -> {
            Point point = new Point(p.getDate(), Double.valueOf(String.format("%.2f", p.getPrice1() == null ? 0 : p.getPrice1())));
            point.setType("预测价格");
            return point;
        }).collect(Collectors.toList());
        // 查找历史数据
        List<StockPrices> historyPrices = modelInfoService.getHistoryData(stockTestPrices);
        List<Point> points = historyPrices.stream().map(p -> {
            Point point = new Point(p.getDate(), p.getPrice1());
            point.setType("实际价格");
            return point;
        }).collect(Collectors.toList());
        pointList.addAll(points);
        PointsDTO pointsDTO = new PointsDTO(pointList);
        return Response.success(pointsDTO);
    }

    @GetMapping("/listValidateData")
    public Response<PointsDTO> listValidateData(StockInfoQuery param) {
        return listTestData(param, VALIDATE_COLLECTION_NAME);
    }
}
