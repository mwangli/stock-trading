package online.mwang.stockTrading.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.mapper.OrderInfoMapper;
import online.mwang.stockTrading.web.service.OrderInfoService;
import org.springframework.stereotype.Service;

@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

}
