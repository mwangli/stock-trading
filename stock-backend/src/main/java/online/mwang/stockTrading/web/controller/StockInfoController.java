package online.mwang.stockTrading.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.base.Response;
import online.mwang.stockTrading.web.bean.dto.PointsDTO;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
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

    private final static String ASCEND = "ascend";
    private final static String TEST_COLLECTION_NAME = "stockTestPrice";
    private final static String TRAIN_COLLECTION_NAME = "stockHistoryPrice";
    private final static int HISTORY_SIZE = 300;
    private final StockInfoService stockInfoService;
    private final MongoTemplate mongoTemplate;


    @GetMapping("/selectStockInfo")
    public Response<Boolean> selectStockInfo(StockInfoQuery query) {
        LambdaQueryWrapper<StockInfo> queryWrapper = new QueryWrapper<StockInfo>().lambda().eq(StockInfo::getCode, query.getCode());
        StockInfo stockInfo = stockInfoService.getOne(queryWrapper);
        stockInfo.setSelected("1");
        return Response.success(stockInfoService.updateById(stockInfo));
    }

    @GetMapping("/cancelStockInfo")
    public Response<Boolean> cancelStockInfo(StockInfoQuery query) {
        LambdaQueryWrapper<StockInfo> queryWrapper = new QueryWrapper<StockInfo>().lambda().eq(StockInfo::getCode, query.getCode());
        StockInfo stockInfo = stockInfoService.getOne(queryWrapper);
        stockInfo.setSelected("0");
        return Response.success(stockInfoService.updateById(stockInfo));
    }

    @GetMapping("/list")
    public Response<List<StockInfo>> listStockInfo(StockInfoQuery query) {
        LambdaQueryWrapper<StockInfo> queryWrapper = new QueryWrapper<StockInfo>().lambda()
                .like(ObjectUtils.isNotNull(query.getCode()), StockInfo::getCode, query.getCode())
                .like(ObjectUtils.isNotNull(query.getName()), StockInfo::getName, query.getName())
                .like(ObjectUtils.isNotNull(query.getMarket()), StockInfo::getMarket, query.getName())
                .eq(ObjectUtils.isNotNull(query.getPermission()), StockInfo::getPermission, query.getPermission())
                .eq(ObjectUtils.isNotNull(query.getSelected()), StockInfo::getSelected, query.getSelected())
                .eq(ObjectUtils.isNotNull(query.getBuySaleCount()), StockInfo::getBuySaleCount, query.getBuySaleCount())
                .ge(ObjectUtils.isNotNull(query.getPriceLow()), StockInfo::getPrice, query.getPriceLow())
                .le(ObjectUtils.isNotNull(query.getPriceHigh()), StockInfo::getPrice, query.getPriceHigh())
                .orderBy(true, ASCEND.equals(query.getSortOrder()), StockInfo.getOrder(query.getSortKey()));
        Page<StockInfo> pageResult = stockInfoService.page(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }

    @GetMapping("/listHistoryPrices")
    public Response<PointsDTO> listHistoryPrices(StockInfoQuery param) {
        List<Point> points = getHistoryData(param.getCode()).stream().map(p -> new Point(p.getDate(), p.getPrice1())).collect(Collectors.toList());
        return Response.success(new PointsDTO(points));
    }

    @GetMapping("/listIncreaseRate")
    public Response<PointsDTO> listIncreaseRate(StockInfoQuery param) {
        List<StockPrices> stockPrices = getHistoryData(param.getCode());
        ArrayList<Point> increaseRateList = new ArrayList<>();
        for (int i = 1; i < stockPrices.size(); i++) {
            double todayPrice = stockPrices.get(i).getPrice1();
            double preDayPrice = stockPrices.get(i - 1).getPrice1();
            double increaseRate = preDayPrice == 0 ? 0 : (todayPrice - preDayPrice) / preDayPrice * 100;
            increaseRateList.add(new Point(stockPrices.get(i).getDate(), Double.parseDouble(String.format("%.4f", increaseRate))));
        }
        return Response.success(new PointsDTO(increaseRateList));
    }

    private List<StockPrices> getHistoryData(String code) {
        Query query = new Query(Criteria.where("code").is(code)).with(Sort.by(Sort.Direction.ASC, "date"));
        long count = mongoTemplate.count(query, StockPrices.class, TRAIN_COLLECTION_NAME);
        return mongoTemplate.find(query.skip(count - HISTORY_SIZE), StockPrices.class, TRAIN_COLLECTION_NAME);
    }

}
