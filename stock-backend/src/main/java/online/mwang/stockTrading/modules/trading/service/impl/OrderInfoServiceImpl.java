package online.mwang.stockTrading.modules.trading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.stockTrading.modules.trading.entity.OrderInfo;
import online.mwang.stockTrading.modules.trading.mapper.OrderInfoMapper;
import online.mwang.stockTrading.modules.trading.service.OrderInfoService;
import org.springframework.stereotype.Service;

/**
 * 订单服务实现
 */
@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {
}
