package online.mwang.foundtrading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.foundtrading.bean.po.ScoreStrategy;
import online.mwang.foundtrading.mapper.ScoreStrategyMapper;
import online.mwang.foundtrading.service.ScoreStrategyService;
import org.springframework.stereotype.Service;

@Service
public class ScoreStrategyServiceImpl extends ServiceImpl<ScoreStrategyMapper, ScoreStrategy> implements ScoreStrategyService {
}
