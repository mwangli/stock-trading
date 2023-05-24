package online.mwang.foundtrading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.foundtrading.bean.po.StockInfo;
import online.mwang.foundtrading.mapper.StockInfoMapper;
import online.mwang.foundtrading.service.StockInfoService;
import org.springframework.stereotype.Service;

@Service
public class StockInfoServiceImpl extends ServiceImpl<StockInfoMapper, StockInfo> implements StockInfoService {

}
