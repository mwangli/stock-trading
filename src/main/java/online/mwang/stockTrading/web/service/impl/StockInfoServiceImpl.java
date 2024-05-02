package online.mwang.stockTrading.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.mapper.ModelInfoMapper;
import online.mwang.stockTrading.web.mapper.StockInfoMapper;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockInfoServiceImpl extends ServiceImpl<StockInfoMapper, StockInfo> implements StockInfoService {

    private final StockInfoMapper stockInfoMapper;
    private final ModelInfoMapper modelInfoMapper;

    @Override
    public List<StockInfo> getTrainStockInfos() {
        List<StockInfo> stockInfos = stockInfoMapper.selectList(new QueryWrapper<>());
        List<ModelInfo> modelInfos = modelInfoMapper.selectList(new QueryWrapper<>());
        Set<String> stockCodes = stockInfos.stream().map(StockInfo::getCode).collect(Collectors.toSet());
        Set<String> modelCodes = modelInfos.stream().map(ModelInfo::getCode).collect(Collectors.toSet());
        stockCodes.removeAll(modelCodes);
        return stockInfos.stream().filter(s -> stockCodes.contains(s.getCode())).collect(Collectors.toList());
    }

}
