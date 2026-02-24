package online.mwang.stockTrading.web.mapper;

import online.mwang.stockTrading.web.bean.po.AccountInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountInfoMapper {
    void save(AccountInfo accountInfo);
    void update(AccountInfo accountInfo);
    AccountInfo getLast();
}
