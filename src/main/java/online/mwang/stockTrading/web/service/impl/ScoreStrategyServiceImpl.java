package online.mwang.stockTrading.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.stockTrading.web.bean.po.ModelStrategy;
import online.mwang.stockTrading.web.mapper.ScoreStrategyMapper;
import online.mwang.stockTrading.web.service.ScoreStrategyService;
import org.springframework.stereotype.Service;

@Service
public class ScoreStrategyServiceImpl extends ServiceImpl<ScoreStrategyMapper, ModelStrategy> implements ScoreStrategyService {
}
