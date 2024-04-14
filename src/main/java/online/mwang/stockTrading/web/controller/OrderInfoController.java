package online.mwang.stockTrading.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.base.Response;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.query.OrderInfoQuery;
import online.mwang.stockTrading.web.service.OrderInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:56
 * @description: FoundTradingController
 */
@Slf4j
@RestController
@RequestMapping("/orderInfo")
@RequiredArgsConstructor
public class OrderInfoController {

    private final OrderInfoService orderInfoService;

    @GetMapping("/list")
    public Response<List<OrderInfo>> listStockInfo(OrderInfoQuery query) {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<OrderInfo>().lambda()
                .like(ObjectUtils.isNotNull(query.getCode()), OrderInfo::getCode, query.getCode())
                .like(ObjectUtils.isNotNull(query.getName()), OrderInfo::getName, query.getName())
                .eq(ObjectUtils.isNotNull(query.getDate()), OrderInfo::getDate, query.getDate())
                .eq(ObjectUtils.isNotNull(query.getAnswerNo()), OrderInfo::getAnswerNo, query.getAnswerNo())
                .ge(ObjectUtils.isNotNull(query.getType()), OrderInfo::getType, query.getType())
                .ge(ObjectUtils.isNotNull(query.getNumber()), OrderInfo::getNumber, query.getNumber())
                .orderBy(true, false, OrderInfo::getCreateTime);
        Page<OrderInfo> pageResult = orderInfoService.page(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }
}
