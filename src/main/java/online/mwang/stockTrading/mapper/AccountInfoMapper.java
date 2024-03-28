package online.mwang.stockTrading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.stockTrading.bean.po.AccountInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 11:13
 * @description: StockInfoMapper
 */
@Mapper
public interface AccountInfoMapper extends BaseMapper<AccountInfo> {
}
