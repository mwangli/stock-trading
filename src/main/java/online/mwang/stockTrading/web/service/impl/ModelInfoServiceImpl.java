package online.mwang.stockTrading.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.mapper.ScoreStrategyMapper;
import online.mwang.stockTrading.web.service.ModelInfoService;
import org.springframework.stereotype.Service;

@Service
public class ModelInfoServiceImpl extends ServiceImpl<ScoreStrategyMapper, ModelInfo> implements ModelInfoService {
}
