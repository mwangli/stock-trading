package online.mwang.stockTrading.modules.trading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.stockTrading.modules.trading.entity.AccountInfo;
import online.mwang.stockTrading.modules.trading.mapper.AccountInfoMapper;
import online.mwang.stockTrading.modules.trading.service.AccountInfoService;
import org.springframework.stereotype.Service;

/**
 * 账户信息服务实现
 */
@Service
public class AccountInfoServiceImpl extends ServiceImpl<AccountInfoMapper, AccountInfo> implements AccountInfoService {
}
