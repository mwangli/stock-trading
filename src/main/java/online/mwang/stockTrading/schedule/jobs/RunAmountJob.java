package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.data.IDataService;
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
public class RunAmountJob extends BaseJob {

    private final IDataService dataService;

    @Override
    public void run() {
        dataService.getAmount();
    }
}
