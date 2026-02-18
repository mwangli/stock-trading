package online.mwang.stockTrading.modules.trading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.stockTrading.modules.trading.entity.AccountInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 账户信息 Mapper
 */
@Mapper
public interface AccountInfoMapper extends BaseMapper<AccountInfo> {

    @Select("select * from account_info order by create_time desc limit 0,1")
    AccountInfo getLast();
}
