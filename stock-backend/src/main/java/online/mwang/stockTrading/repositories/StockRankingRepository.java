package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.StockRanking;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * V3.0 股票综合评分排名 Repository
 * 从MySQL读取Python写入的排名数据
 */
@Repository
@Transactional
public class StockRankingRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(StockRanking ranking) {
        entityManager.persist(ranking);
    }

    public StockRanking findById(Long id) {
        return entityManager.find(StockRanking.class, id);
    }

    public StockRanking findByStockCodeAndDate(String stockCode, LocalDate date) {
        Query query = entityManager.createQuery(
            "SELECT r FROM StockRanking r WHERE r.stockCode = :stockCode AND r.rankDate = :date");
        query.setParameter("stockCode", stockCode);
        query.setParameter("date", date);
        List<StockRanking> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    public List<StockRanking> findByDate(LocalDate date) {
        Query query = entityManager.createQuery(
            "SELECT r FROM StockRanking r WHERE r.rankDate = :date ORDER BY r.rankPosition ASC");
        query.setParameter("date", date);
        return query.getResultList();
    }

    public List<StockRanking> findTopByDate(LocalDate date, int limit) {
        Query query = entityManager.createQuery(
            "SELECT r FROM StockRanking r WHERE r.rankDate = :date ORDER BY r.rankPosition ASC");
        query.setParameter("date", date);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    public List<StockRanking> findTopCompositeScoreByDate(LocalDate date, int limit) {
        Query query = entityManager.createQuery(
            "SELECT r FROM StockRanking r WHERE r.rankDate = :date ORDER BY r.compositeScore DESC");
        query.setParameter("date", date);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    public List<StockRanking> findByStockCodesAndDate(List<String> stockCodes, LocalDate date) {
        Query query = entityManager.createQuery(
            "SELECT r FROM StockRanking r WHERE r.stockCode IN :stockCodes AND r.rankDate = :date");
        query.setParameter("stockCodes", stockCodes);
        query.setParameter("date", date);
        return query.getResultList();
    }

    public StockRanking update(StockRanking ranking) {
        return entityManager.merge(ranking);
    }

    public void delete(StockRanking ranking) {
        entityManager.remove(ranking);
    }
}
