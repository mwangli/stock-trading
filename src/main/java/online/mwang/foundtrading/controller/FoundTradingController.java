package online.mwang.foundtrading.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    public JSONObject listFound(FoundTradingQuery query) {
        Page<FoundTradingRecord> page = new Page<>(query.getCurrent(), query.getPageSize());
        Page<FoundTradingRecord> pageResult = foundTradingService.page(page);
        List<FoundTradingRecord> records = pageResult.getRecords();
        final JSONObject res = new JSONObject();
        res.put("data", pageResult.getRecords());
        res.put("total", pageResult.getTotal());
        res.put("success", true);
        return res;
    }
}
