package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.OrderInfo;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单信息 Repository (Hibernate原生)
 */
@Repository
@Transactional
public class OrderInfoRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(OrderInfo orderInfo) {
        entityManager.persist(orderInfo);
    }

    public OrderInfo findById(Long id) {
        return entityManager.find(OrderInfo.class, id);
    }

    public OrderInfo update(OrderInfo orderInfo) {
        return entityManager.merge(orderInfo);
    }

    public void delete(OrderInfo orderInfo) {
        entityManager.remove(orderInfo);
    }

    public List<OrderInfo> findAll() {
        Query query = entityManager.createQuery("SELECT o FROM OrderInfo o");
        return query.getResultList();
    }

    public List<String> listAnswerNo() {
        Query query = entityManager.createQuery("SELECT o.answerNo FROM OrderInfo o");
        return query.getResultList();
    }

    public Long count() {
        Query query = entityManager.createQuery("SELECT COUNT(o) FROM OrderInfo o");
        return (Long) query.getSingleResult();
    }
}
