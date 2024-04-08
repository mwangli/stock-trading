package online.mwang.stockTrading.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 11:13
 * @description: StockInfoMapper
 */
@Mapper
public interface AccountInfoMapper extends BaseMapper<AccountInfo> {

    @Select("select * from account_info order by create_time desc limit 0,1")
    AccountInfo getLast();
}
