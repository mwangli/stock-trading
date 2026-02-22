package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.TradeOrder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 委托订单 Repository
 */
@Repository
@Transactional
public class TradeOrderRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(TradeOrder order) {
        entityManager.persist(order);
    }

    public TradeOrder findById(Long id) {
        return entityManager.find(TradeOrder.class, id);
    }

    public TradeOrder findByOrderId(String orderId) {
        Query query = entityManager.createQuery(
            "SELECT t FROM TradeOrder t WHERE t.orderId = :orderId");
        query.setParameter("orderId", orderId);
        query.setMaxResults(1);
        List results = query.getResultList();
        return results.isEmpty() ? null : (TradeOrder) results.get(0);
    }

    public List<TradeOrder> findAll() {
        Query query = entityManager.createQuery(
            "SELECT t FROM TradeOrder t ORDER BY t.orderDate DESC, t.orderTime DESC");
        return query.getResultList();
    }

    public List<TradeOrder> findByDate(LocalDate date) {
        Query query = entityManager.createQuery(
            "SELECT t FROM TradeOrder t WHERE t.orderDate = :date ORDER BY t.orderTime DESC");
        query.setParameter("date", date);
        return query.getResultList();
    }

    public List<TradeOrder> findByStockCode(String stockCode) {
        Query query = entityManager.createQuery(
            "SELECT t FROM TradeOrder t WHERE t.stockCode = :stockCode ORDER BY t.orderDate DESC");
        query.setParameter("stockCode", stockCode);
        return query.getResultList();
    }

    public List<TradeOrder> findByStatus(String status) {
        Query query = entityManager.createQuery(
            "SELECT t FROM TradeOrder t WHERE t.status = :status ORDER BY t.orderDate DESC");
        query.setParameter("status", status);
        return query.getResultList();
    }

    public TradeOrder update(TradeOrder order) {
        return entityManager.merge(order);
    }

    public void delete(TradeOrder order) {
        entityManager.remove(order);
    }
}
