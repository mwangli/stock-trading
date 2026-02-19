package online.mwang.stockTrading.services;

import online.mwang.stockTrading.entities.AccountInfo;

import java.util.List;

/**
 * 账户信息服务接口
 */
public interface AccountInfoService {

    void save(AccountInfo accountInfo);

    AccountInfo findById(Long id);

    AccountInfo update(AccountInfo accountInfo);

    void delete(AccountInfo accountInfo);

    List<AccountInfo> findAll();

    AccountInfo getLast();

    Long count();
}
