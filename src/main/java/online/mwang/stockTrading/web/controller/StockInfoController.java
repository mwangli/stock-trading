package online.mwang.stockTrading.web.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.base.Response;
import online.mwang.stockTrading.web.bean.dto.StockPricesDTO;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.query.StockInfoQuery;
import online.mwang.stockTrading.web.bean.vo.Point;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:56
 * @description: FoundTradingController
 */
@Slf4j
@RestController
@RequestMapping("/stockInfo")
@RequiredArgsConstructor
public class StockInfoController {

    private final StockInfoService stockInfoService;
    private final MongoTemplate mongoTemplate;
    private final static String ASCEND = "ascend";
    private final static String TEST_COLLECTION_NAME = "stockTestPrice";
    private final static String TRAIN_COLLECTION_NAME = "stockHistoryPrice";


    @GetMapping("/list")
    public Response<List<StockInfo>> listStockInfo(StockInfoQuery query) {
        LambdaQueryWrapper<StockInfo> queryWrapper = new QueryWrapper<StockInfo>().lambda()
                .like(ObjectUtils.isNotNull(query.getCode()), StockInfo::getCode, query.getCode())
                .like(ObjectUtils.isNotNull(query.getName()), StockInfo::getName, query.getName())
                .like(ObjectUtils.isNotNull(query.getMarket()), StockInfo::getMarket, query.getName())
                .eq(ObjectUtils.isNotNull(query.getPermission()), StockInfo::getPermission, query.getPermission())
                .eq(ObjectUtils.isNotNull(query.getBuySaleCount()), StockInfo::getBuySaleCount, query.getBuySaleCount())
                .ge(ObjectUtils.isNotNull(query.getPriceLow()), StockInfo::getPrice, query.getPriceLow())
                .le(ObjectUtils.isNotNull(query.getPriceHigh()), StockInfo::getPrice, query.getPriceHigh())
                .orderBy(true, ASCEND.equals(query.getSortOrder()), StockInfo.getOrder(query.getSortKey()));
        Page<StockInfo> pageResult = stockInfoService.page(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }

    @GetMapping("/listHistoryPrices")
    public Response<StockPricesDTO> listHistoryPrices(StockInfoQuery param) {
        String stockCode = param.getCode();
        Query query = new Query(Criteria.where("code").is(stockCode)).with(Sort.by(Sort.Direction.ASC, "date"));
        List<StockPrices> stockPrices = mongoTemplate.find(query, StockPrices.class);
        List<Point> points = stockPrices.stream().map(p -> new Point(p.getDate(), p.getPrice1())).collect(Collectors.toList());
        StockPricesDTO stockPricesDTO = new StockPricesDTO(points);
        return Response.success(stockPricesDTO);
    }

    @GetMapping("/listTestPrices")
    public Response<StockPricesDTO> listTestPrices(StockInfoQuery param) {
        String stockCode = param.getCode();
        // 查找测试集数据
        final Query query = new Query(Criteria.where("code").is(stockCode)).with(Sort.by(Sort.Direction.ASC, "date"));
        List<StockPrices> stockTestPrices = mongoTemplate.find(query, StockPrices.class, TEST_COLLECTION_NAME);
        String maxDate = stockTestPrices.stream().map(StockPrices::getDate).max(String::compareTo).orElse("");
        String minDate = stockTestPrices.stream().map(StockPrices::getDate).min(String::compareTo).orElse("");
        // 查找历史数据
        Query historyQuery = new Query(Criteria.where("code").is(stockCode).and("date").lte(maxDate).gte(minDate));
        List<StockPrices> stockHistoryPrices = mongoTemplate.find(historyQuery, StockPrices.class, TRAIN_COLLECTION_NAME);
        final ArrayList<Point> points = new ArrayList<>();
        for (int i = 0; i < stockTestPrices.size(); i++) {
            StockPrices stockTestPrice = stockTestPrices.get(i);
            StockPrices stockHistoryPrice = stockHistoryPrices.get(i);
            final Point point1 = new Point(stockTestPrice.getDate(), stockTestPrice.getPrice1());
            final Point point2 = new Point(stockHistoryPrice.getDate(), stockHistoryPrice.getPrice1());
            point1.setType("预测价格");
            point2.setType("实际价格");
            points.add(point1);
            points.add(point2);
        }
        StockPricesDTO stockPricesDTO = new StockPricesDTO(points);
        return Response.success(stockPricesDTO);
    }
}
