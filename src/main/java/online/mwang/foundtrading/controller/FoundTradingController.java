package online.mwang.foundtrading.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import online.mwang.foundtrading.bean.FoundTradingRecord;
import online.mwang.foundtrading.bean.query.FoundTradingQuery;
import online.mwang.foundtrading.service.FoundTradingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public Integer createFound(@RequestBody FoundTradingRecord foundTradingRecord) {
        return foundTradingService.createFound(foundTradingRecord);
    }

    @PutMapping
    public Integer updateFound(@RequestBody FoundTradingRecord foundTradingRecord) {
        return foundTradingService.updateFound(foundTradingRecord);
    }

    @DeleteMapping("{id}")
    public Integer deleteFound(@PathVariable String id) {
        return foundTradingService.deleteFound(id);
    }

    @GetMapping
    public Page<FoundTradingRecord> listFound(FoundTradingQuery query) {
        return foundTradingService.listFound(query);
    }
}
