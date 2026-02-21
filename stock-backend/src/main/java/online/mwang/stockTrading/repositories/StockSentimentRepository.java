package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.StockSentiment;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * V3.0 情感分析结果 Repository
 * 从MySQL读取Python写入的情感分析数据
 */
@Repository
@Transactional
public class StockSentimentRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(StockSentiment sentiment) {
        entityManager.persist(sentiment);
    }

    public StockSentiment findById(Long id) {
        return entityManager.find(StockSentiment.class, id);
    }

    public StockSentiment findByStockCodeAndDate(String stockCode, LocalDate date) {
        Query query = entityManager.createQuery(
            "SELECT s FROM StockSentiment s WHERE s.stockCode = :stockCode AND s.analyzeDate = :date");
        query.setParameter("stockCode", stockCode);
        query.setParameter("date", date);
        List<StockSentiment> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    public List<StockSentiment> findByDate(LocalDate date) {
        Query query = entityManager.createQuery(
            "SELECT s FROM StockSentiment s WHERE s.analyzeDate = :date ORDER BY s.sentimentScore DESC");
        query.setParameter("date", date);
        return query.getResultList();
    }

    public List<StockSentiment> findTopByDate(LocalDate date, int limit) {
        Query query = entityManager.createQuery(
            "SELECT s FROM StockSentiment s WHERE s.analyzeDate = :date ORDER BY s.sentimentScore DESC");
        query.setParameter("date", date);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    public List<StockSentiment> findByStockCodesAndDate(List<String> stockCodes, LocalDate date) {
        Query query = entityManager.createQuery(
            "SELECT s FROM StockSentiment s WHERE s.stockCode IN :stockCodes AND s.analyzeDate = :date");
        query.setParameter("stockCodes", stockCodes);
        query.setParameter("date", date);
        return query.getResultList();
    }

    public StockSentiment update(StockSentiment sentiment) {
        return entityManager.merge(sentiment);
    }

    public void delete(StockSentiment sentiment) {
        entityManager.remove(sentiment);
    }
}
