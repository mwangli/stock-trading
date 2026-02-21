package online.mwang.stockTrading.services.impl;

import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.entities.AccountInfo;
import online.mwang.stockTrading.repositories.AccountInfoRepository;
import online.mwang.stockTrading.services.AccountInfoService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 账户信息服务实现
 */
@Service
@RequiredArgsConstructor
public class AccountInfoServiceImpl implements AccountInfoService {

    private final AccountInfoRepository accountInfoRepository;

    @Override
    public void save(AccountInfo accountInfo) {
        accountInfoRepository.save(accountInfo);
    }

    @Override
    public AccountInfo findById(Long id) {
        return accountInfoRepository.findById(id);
    }

    @Override
    public AccountInfo update(AccountInfo accountInfo) {
        return accountInfoRepository.update(accountInfo);
    }

    @Override
    public void delete(AccountInfo accountInfo) {
        accountInfoRepository.delete(accountInfo);
    }

    @Override
    public List<AccountInfo> findAll() {
        return accountInfoRepository.findAll();
    }

    @Override
    public AccountInfo getLast() {
        return accountInfoRepository.getLast();
    }

    @Override
    public Long count() {
        return accountInfoRepository.count();
    }
}
