package online.mwang.foundDay.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import online.mwang.foundtrading.bean.FoundDayRecord;
import online.mwang.foundtrading.bean.query.FoundDayQuery;
import online.mwang.foundtrading.mapper.FoundDayMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:57
 * @description: FoundDayService
 */
@Service
public class FoundDayService {

    @Autowired
    private FoundDayMapper foundDayMapper;

    public Integer createFound(FoundDayRecord foundDayRecord) {
        return foundDayMapper.insert(foundDayRecord);
    }

    public Integer updateFound(FoundDayRecord foundDayRecord) {
        return foundDayMapper.updateById(foundDayRecord);
    }

    public Integer deleteFound(String id) {
        return foundDayMapper.deleteById(id);
    }

    public Page<FoundDayRecord> listFound(FoundDayQuery query) {
        Page<FoundDayRecord> page = new Page<>(query.getPageIndex(), query.getPageSize());
        QueryWrapper<FoundDayRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("code", query.getCode());
        queryWrapper.like("name", query.getName());
        return foundDayMapper.selectPage(page, queryWrapper);
    }
}
