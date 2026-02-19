package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.AccountInfo;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 账户信息 Repository (Hibernate原生)
 */
@Repository
@Transactional
public class AccountInfoRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(AccountInfo accountInfo) {
        entityManager.persist(accountInfo);
    }

    public AccountInfo findById(Long id) {
        return entityManager.find(AccountInfo.class, id);
    }

    public AccountInfo update(AccountInfo accountInfo) {
        return entityManager.merge(accountInfo);
    }

    public void delete(AccountInfo accountInfo) {
        entityManager.remove(accountInfo);
    }

    public List<AccountInfo> findAll() {
        Query query = entityManager.createQuery("SELECT a FROM AccountInfo a ORDER BY a.createTime DESC");
        return query.getResultList();
    }

    public AccountInfo getLast() {
        Query query = entityManager.createQuery("SELECT a FROM AccountInfo a ORDER BY a.createTime DESC");
        query.setMaxResults(1);
        List<AccountInfo> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    public Long count() {
        Query query = entityManager.createQuery("SELECT COUNT(a) FROM AccountInfo a");
        return (Long) query.getSingleResult();
    }
}
