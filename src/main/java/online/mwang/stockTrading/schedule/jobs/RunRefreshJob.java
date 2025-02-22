package online.mwang.stockTrading.schedule.jobs;

import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import org.springframework.stereotype.Component;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunRefreshJob extends BaseJob {

    private final IStockService stockService;

    @SneakyThrows
    @Override
    public void run() {
        refreshToken();
    }

    // 目前阶段由于无法实现登录接口的滑块验证码技术突破，使用手动注入Token，定期刷新Token的方式来说维系业务运转
    // Token有效期为30分钟，该定时任务设置每隔25分钟执行一次
    private void refreshToken() {
        // 此处的购买参数没有实际意义，仅为了调用购买结果获得一个新的Token
        JSONObject result = stockService.buySale("B", "000000", 100.00, 1000000.00);
        String token = result.getString("TOKEN");
        if (token != null) stockService.setToken(token);
        else log.info("Token刷新失败，请检查程序代码");
    }
}
