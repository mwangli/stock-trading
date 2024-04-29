package online.mwang.stockTrading.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.web.bean.base.Response;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.bean.query.ModelInfoQuery;
import online.mwang.stockTrading.web.service.ModelInfoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:56
 * @description: StrategyController
 */
@RestController
@RequestMapping("/strategy")
@RequiredArgsConstructor
public class ModelInfoController {

    private final static String ASCEND = "ascend";
    private final static String DESCEND = "descend";
    private final ModelInfoService modelInfoService;


    @GetMapping("/list")
    public Response<List<ModelInfo>> list(ModelInfoQuery query) {
        LambdaQueryWrapper<ModelInfo> queryWrapper = new QueryWrapper<ModelInfo>().lambda()
                .like(ObjectUtils.isNotNull(query.getName()), ModelInfo::getName, query.getName())
                .eq((ObjectUtils.isNotNull(query.getStatus())), ModelInfo::getStatus, query.getStatus())
                .orderBy(true, !DESCEND.equals(query.getSortOrder()), ModelInfo.getOrder(query.getSortKey()));
        Page<ModelInfo> pageResult = modelInfoService.page(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }
}
