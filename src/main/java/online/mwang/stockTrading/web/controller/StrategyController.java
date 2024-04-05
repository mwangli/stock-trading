package online.mwang.stockTrading.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.web.bean.base.Response;
import online.mwang.stockTrading.web.bean.po.ScoreStrategy;
import online.mwang.stockTrading.web.bean.query.StrategyQuery;
import online.mwang.stockTrading.web.service.ScoreStrategyService;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:56
 * @description: FoundTradingController
 */
@RestController
@RequestMapping("strategy")
@RequiredArgsConstructor
public class StrategyController {

    private final static String ASCEND = "ascend";
    private final static String DESCEND = "descend";
    private final ScoreStrategyService strategyService;

    @PostMapping("choose")
    public Boolean choose(@RequestBody ScoreStrategy strategy) {
        cancelChoose();
        strategy.setStatus(1);
        return strategyService.update(strategy, new QueryWrapper<ScoreStrategy>().lambda().eq(ScoreStrategy::getId, strategy.getId()));
    }

    private void cancelChoose() {
        // 取消选中
        final List<ScoreStrategy> oldChooseList = strategyService.list(new LambdaQueryWrapper<ScoreStrategy>().eq(ScoreStrategy::getStatus, "1"));
        oldChooseList.forEach(o -> {
            o.setStatus(0);
            strategyService.updateById(o);
        });
    }

    @PostMapping
    public Boolean create(@RequestBody ScoreStrategy strategy) {
        final Date now = new Date();
        strategy.setCreateTime(now);
        strategy.setUpdateTime(now);
        strategy.setDeleted(1);
        if (strategy.getStatus() == 1) {
            cancelChoose();
        }
        return strategyService.save(strategy);
    }

    @PutMapping
    public Boolean update(@RequestBody ScoreStrategy strategy) {
        if (strategy.getStatus() == 1) cancelChoose();
        strategy.setUpdateTime(new Date());
        return strategyService.updateById(strategy);
    }

    @DeleteMapping()
    public Boolean delete(@RequestBody ScoreStrategy strategy) {
            strategy.setDeleted(0);
        return strategyService.updateById(strategy);
    }

    @GetMapping
    public Response<List<ScoreStrategy>> list(StrategyQuery query) {
        LambdaQueryWrapper<ScoreStrategy> queryWrapper = new QueryWrapper<ScoreStrategy>().lambda()
                .like(ObjectUtils.isNotNull(query.getName()), ScoreStrategy::getName, query.getName())
                .like(ObjectUtils.isNotNull(query.getParams()), ScoreStrategy::getParams, query.getParams())
                .eq((ObjectUtils.isNotNull(query.getStatus())), ScoreStrategy::getStatus, query.getStatus())
                .eq(ScoreStrategy::getDeleted, "1")
                .orderBy(true, !DESCEND.equals(query.getSortOrder()), ScoreStrategy.getOrder(query.getSortKey()));
        Page<ScoreStrategy> pageResult = strategyService.page(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }
}
