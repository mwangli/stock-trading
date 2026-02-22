package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.PerformanceRecord;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 表现记录 Repository
 * 从MySQL读取Python写入的模型表现数据
 */
@Repository
@Transactional
public class PerformanceRecordRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(PerformanceRecord record) {
        entityManager.persist(record);
    }

    public PerformanceRecord findById(Long id) {
        return entityManager.find(PerformanceRecord.class, id);
    }

    public List<PerformanceRecord> findAll() {
        Query query = entityManager.createQuery(
            "SELECT p FROM PerformanceRecord p ORDER BY p.tradeDate DESC");
        return query.getResultList();
    }

    public List<PerformanceRecord> findRecentRecords(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Query query = entityManager.createQuery(
            "SELECT p FROM PerformanceRecord p WHERE p.tradeDate >= :since ORDER BY p.tradeDate DESC");
        query.setParameter("since", since);
        return query.getResultList();
    }

    public PerformanceRecord findLatest() {
        Query query = entityManager.createQuery(
            "SELECT p FROM PerformanceRecord p ORDER BY p.tradeDate DESC");
        query.setMaxResults(1);
        List results = query.getResultList();
        return results.isEmpty() ? null : (PerformanceRecord) results.get(0);
    }

    public PerformanceRecord update(PerformanceRecord record) {
        return entityManager.merge(record);
    }

    public void delete(PerformanceRecord record) {
        entityManager.remove(record);
    }
}
