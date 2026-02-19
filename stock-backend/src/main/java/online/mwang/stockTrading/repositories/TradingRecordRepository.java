package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.TradingRecord;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 交易记录 Repository (Hibernate原生)
 */
@Repository
@Transactional
public class TradingRecordRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(TradingRecord tradingRecord) {
        entityManager.persist(tradingRecord);
    }

    public TradingRecord findById(Long id) {
        return entityManager.find(TradingRecord.class, id);
    }

    public TradingRecord update(TradingRecord tradingRecord) {
        return entityManager.merge(tradingRecord);
    }

    public void delete(TradingRecord tradingRecord) {
        entityManager.remove(tradingRecord);
    }

    public List<TradingRecord> findAll() {
        Query query = entityManager.createQuery("SELECT t FROM TradingRecord t");
        return query.getResultList();
    }

    public Long count() {
        Query query = entityManager.createQuery("SELECT COUNT(t) FROM TradingRecord t");
        return (Long) query.getSingleResult();
    }
}
