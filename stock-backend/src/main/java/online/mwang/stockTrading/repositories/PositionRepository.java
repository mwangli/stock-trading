package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.Position;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 持仓信息 Repository
 */
@Repository
@Transactional
public class PositionRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(Position position) {
        entityManager.persist(position);
    }

    public Position findById(Long id) {
        return entityManager.find(Position.class, id);
    }

    public Position update(Position position) {
        return entityManager.merge(position);
    }

    public void delete(Position position) {
        entityManager.remove(position);
    }

    public List<Position> findAll() {
        Query query = entityManager.createQuery("SELECT p FROM Position p");
        return query.getResultList();
    }

    /**
     * 获取当前持仓列表
     */
    public List<Position> findHoldingPositions() {
        Query query = entityManager.createQuery(
            "SELECT p FROM Position p WHERE p.status = 'HOLDING' ORDER BY p.updateTime DESC");
        return query.getResultList();
    }

    /**
     * 根据股票代码查找持仓
     */
    public Position findByStockCode(String stockCode) {
        Query query = entityManager.createQuery(
            "SELECT p FROM Position p WHERE p.stockCode = :stockCode");
        query.setParameter("stockCode", stockCode);
        query.setMaxResults(1);
        List results = query.getResultList();
        return results.isEmpty() ? null : (Position) results.get(0);
    }

    /**
     * 获取所有持仓（包含已平仓）
     */
    public List<Position> findByStatus(String status) {
        Query query = entityManager.createQuery(
            "SELECT p FROM Position p WHERE p.status = :status ORDER BY p.updateTime DESC");
        query.setParameter("status", status);
        return query.getResultList();
    }

    public Long count() {
        Query query = entityManager.createQuery("SELECT COUNT(p) FROM Position p");
        return (Long) query.getSingleResult();
    }
}
