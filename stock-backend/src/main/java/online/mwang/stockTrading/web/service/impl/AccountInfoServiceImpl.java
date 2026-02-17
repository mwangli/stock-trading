package online.mwang.stockTrading.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import online.mwang.stockTrading.web.mapper.OrderInfoMapper;
import online.mwang.stockTrading.web.service.AccountInfoService;
import online.mwang.stockTrading.web.service.OrderInfoService;
import org.springframework.stereotype.Service;

@Service
public class AccountInfoServiceImpl extends ServiceImpl<AccountInfoMapper, AccountInfo> implements AccountInfoService {
}
