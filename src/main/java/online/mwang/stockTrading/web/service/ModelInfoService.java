package online.mwang.stockTrading.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import org.springframework.stereotype.Service;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:57
 * @description: FoundTradingService
 */
@Service
public interface ModelInfoService extends IService<ModelInfo> {

    /**
     * 更细模型评分
     */
    void updateModelScore(String stockCode);
}
