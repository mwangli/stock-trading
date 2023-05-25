package online.mwang.foundtrading.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import online.mwang.foundtrading.bean.base.Response;
import online.mwang.foundtrading.bean.po.FoundTradingRecord;
import online.mwang.foundtrading.bean.query.FoundTradingQuery;
import online.mwang.foundtrading.service.FoundTradingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:56
 * @description: FoundTradingController
 */
@RestController
@RequestMapping("foundTrading")
public class FoundTradingController {

    @Autowired
    private FoundTradingService foundTradingService;


    @PostMapping
    public Boolean createFound(@RequestBody FoundTradingRecord foundTradingRecord) {
        return foundTradingService.save(foundTradingRecord);
    }

    @PutMapping
    public Boolean updateFound(@RequestBody FoundTradingRecord foundTradingRecord) {
        return foundTradingService.updateById(foundTradingRecord);
    }

    @DeleteMapping("{id}")
    public Boolean deleteFound(@PathVariable String id) {
        return foundTradingService.removeById(id);
    }

    @GetMapping
    public Response<List<FoundTradingRecord>> listFound(FoundTradingQuery query) {
        Page<FoundTradingRecord> page = new Page<>(query.getCurrent(), query.getPageSize());
        LambdaQueryWrapper<FoundTradingRecord> queryWrapper = new QueryWrapper<FoundTradingRecord>().lambda()
                .like(ObjectUtils.isNotNull(query.getCode()), FoundTradingRecord::getCode, query.getCode())
                .like(ObjectUtils.isNotNull(query.getName()), FoundTradingRecord::getCode, query.getName())
                .like(ObjectUtils.isNotNull(query.getBuyDate()), FoundTradingRecord::getBuyDate, query.getBuyDate())
                .like(ObjectUtils.isNotNull(query.getSalDate()), FoundTradingRecord::getSaleDate, query.getSalDate())
                .eq(ObjectUtils.isNotNull(query.getHoldDays()), FoundTradingRecord::getHoldDays, query.getHoldDays())
                .eq(ObjectUtils.isNotNull(query.getSold()), FoundTradingRecord::getSold, query.getSold())
                .orderBy(true, "ascend".equals(query.getSortOrder()), FoundTradingRecord.getOrder(query.getSortKey()));
        Page<FoundTradingRecord> pageResult = foundTradingService.page(page, queryWrapper);
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }
}
