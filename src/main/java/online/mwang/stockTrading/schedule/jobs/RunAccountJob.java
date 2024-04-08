package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IDataService;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunAccountJob extends BaseJob {

    private final IDataService dataService;
    private final AccountInfoMapper accountInfoMapper;

    @Override
    public void run() {
        AccountInfo accountInfo = dataService.getAccountInfo();
        final Date now = new Date();
        accountInfo.setCreateTime(now);
        accountInfo.setUpdateTime(now);
        accountInfoMapper.insert(accountInfo);
        log.info("当前可用金额:{}元,持仓金额:{}元,总金额:{}元。", accountInfo.getAvailableAmount(), accountInfo.getUsedAmount(), accountInfo.getTotalAmount());
    }
}
