package online.mwang.foundtrading.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import online.mwang.foundtrading.bean.FoundTradingRecord;
import online.mwang.foundtrading.bean.query.FoundTradingQuery;
import online.mwang.foundtrading.mapper.FoundTradingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:57
 * @description: FoundTradingService
 */
@Service
public class FoundTradingService {

    @Autowired
    private FoundTradingMapper foundTradingMapper;

    public Integer createFound(FoundTradingRecord foundTradingRecord) {
        return foundTradingMapper.insert(foundTradingRecord);
    }

    public Integer updateFound(FoundTradingRecord foundTradingRecord) {
        return foundTradingMapper.updateById(foundTradingRecord);
    }

    public Integer deleteFound(String id) {
        return foundTradingMapper.deleteById(id);
    }

    public JSONObject listFound(FoundTradingQuery query) {
        Page<FoundTradingRecord> page = new Page<>(query.getCurrent(), query.getPageSize());
        QueryWrapper<FoundTradingRecord> queryWrapper = new QueryWrapper<>();
//        queryWrapper.like("code", query.getCode());
//        queryWrapper.like("name", query.getName());
        final Page<FoundTradingRecord> pageRes = foundTradingMapper.selectPage(page, queryWrapper);
        final JSONObject res = new JSONObject();
        res.put("data", pageRes.getRecords());
        res.put("total", pageRes.getTotal());
        res.put("success", true);
        return res;
    }
}
