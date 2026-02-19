package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.StockInfo;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 股票信息 Repository (Hibernate原生)
 */
@Repository
@Transactional
public class StockInfoRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(StockInfo stockInfo) {
        entityManager.persist(stockInfo);
    }

    public StockInfo findById(Long id) {
        return entityManager.find(StockInfo.class, id);
    }

    public StockInfo findByCode(String code) {
        Query query = entityManager.createQuery("SELECT s FROM StockInfo s WHERE s.code = :code");
        query.setParameter("code", code);
        List<StockInfo> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    public String findNameByCode(String code) {
        Query query = entityManager.createQuery("SELECT s.name FROM StockInfo s WHERE s.code = :code");
        query.setParameter("code", code);
        List<String> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    public List<StockInfo> findAll() {
        Query query = entityManager.createQuery("SELECT s FROM StockInfo s");
        return query.getResultList();
    }

    public List<StockInfo> findByDeletedAndIsTradable(String deleted, int isTradable) {
        Query query = entityManager.createQuery("SELECT s FROM StockInfo s WHERE s.deleted = :deleted AND s.isTradable = :isTradable");
        query.setParameter("deleted", deleted);
        query.setParameter("isTradable", isTradable == 1);
        return query.getResultList();
    }

    public StockInfo findByCodeAndDeleted(String code, String deleted) {
        Query query = entityManager.createQuery("SELECT s FROM StockInfo s WHERE s.code = :code AND s.deleted = :deleted");
        query.setParameter("code", code);
        query.setParameter("deleted", deleted);
        List<StockInfo> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    public int deleteByCode(String code) {
        StockInfo stock = findByCode(code);
        if (stock != null) {
            entityManager.remove(stock);
            return 1;
        }
        return 0;
    }

    public StockInfo update(StockInfo stockInfo) {
        return entityManager.merge(stockInfo);
    }

    public Long count() {
        Query query = entityManager.createQuery("SELECT COUNT(s) FROM StockInfo s");
        return (Long) query.getSingleResult();
    }

    public void delete(StockInfo stockInfo) {
        entityManager.remove(stockInfo);
    }
}
