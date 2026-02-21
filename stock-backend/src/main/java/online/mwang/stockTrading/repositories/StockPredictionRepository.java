package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.StockPrediction;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * V3.0 LSTM预测结果 Repository
 * 从MySQL读取Python写入的预测数据
 */
@Repository
@Transactional
public class StockPredictionRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(StockPrediction prediction) {
        entityManager.persist(prediction);
    }

    public StockPrediction findById(Long id) {
        return entityManager.find(StockPrediction.class, id);
    }

    public StockPrediction findByStockCodeAndDate(String stockCode, LocalDate date) {
        Query query = entityManager.createQuery(
            "SELECT p FROM StockPrediction p WHERE p.stockCode = :stockCode AND p.predictDate = :date");
        query.setParameter("stockCode", stockCode);
        query.setParameter("date", date);
        List<StockPrediction> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    public List<StockPrediction> findByDate(LocalDate date) {
        Query query = entityManager.createQuery(
            "SELECT p FROM StockPrediction p WHERE p.predictDate = :date ORDER BY p.confidence DESC");
        query.setParameter("date", date);
        return query.getResultList();
    }

    public List<StockPrediction> findByDateAndDirection(LocalDate date, String direction) {
        Query query = entityManager.createQuery(
            "SELECT p FROM StockPrediction p WHERE p.predictDate = :date AND p.predictDirection = :direction ORDER BY p.confidence DESC");
        query.setParameter("date", date);
        query.setParameter("direction", direction);
        return query.getResultList();
    }

    public List<StockPrediction> findTopByDate(LocalDate date, int limit) {
        Query query = entityManager.createQuery(
            "SELECT p FROM StockPrediction p WHERE p.predictDate = :date ORDER BY p.confidence DESC");
        query.setParameter("date", date);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    public List<StockPrediction> findByStockCodesAndDate(List<String> stockCodes, LocalDate date) {
        Query query = entityManager.createQuery(
            "SELECT p FROM StockPrediction p WHERE p.stockCode IN :stockCodes AND p.predictDate = :date");
        query.setParameter("stockCodes", stockCodes);
        query.setParameter("date", date);
        return query.getResultList();
    }

    public StockPrediction update(StockPrediction prediction) {
        return entityManager.merge(prediction);
    }

    public void delete(StockPrediction prediction) {
        entityManager.remove(prediction);
    }
}
