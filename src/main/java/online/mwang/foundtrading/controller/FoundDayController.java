package online.mwang.foundDay.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import online.mwang.foundDay.service.FoundDayService;
import online.mwang.foundtrading.bean.FoundDayRecord;
import online.mwang.foundtrading.bean.query.FoundDayQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:56
 * @description: FoundDayController
 */
@RestController
@RequestMapping("foundDay")
public class FoundDayController {

    @Autowired
    private FoundDayService foundDayService;


    @PostMapping()
    public Integer createFound(@RequestBody FoundDayRecord foundDayRecord) {
        return foundDayService.createFound(foundDayRecord);
    }

    @PutMapping()
    public Integer updateFound(@RequestBody FoundDayRecord foundDayRecord) {
        return foundDayService.updateFound(foundDayRecord);
    }

    @DeleteMapping("{id}")
    public Integer deleteFound(@PathVariable String id) {
        return foundDayService.deleteFound(id);
    }

    @GetMapping()
    public Page<FoundDayRecord> listFound(@RequestParam FoundDayQuery query) {
        return foundDayService.listFound(query);
    }
}
