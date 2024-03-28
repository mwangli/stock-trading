package online.mwang.stockTrading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.stockTrading.bean.po.ScoreStrategy;
import online.mwang.stockTrading.mapper.ScoreStrategyMapper;
import online.mwang.stockTrading.service.ScoreStrategyService;
import org.springframework.stereotype.Service;

@Service
public class ScoreStrategyServiceImpl extends ServiceImpl<ScoreStrategyMapper, ScoreStrategy> implements ScoreStrategyService {
}
