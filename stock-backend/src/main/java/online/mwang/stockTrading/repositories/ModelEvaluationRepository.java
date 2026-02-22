package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.ModelEvaluation;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型评估结果 Repository
 * 从MySQL读取Python写入的模型评估数据
 */
@Repository
@Transactional
public class ModelEvaluationRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(ModelEvaluation evaluation) {
        entityManager.persist(evaluation);
    }

    public ModelEvaluation findById(Long id) {
        return entityManager.find(ModelEvaluation.class, id);
    }

    public List<ModelEvaluation> findAll() {
        Query query = entityManager.createQuery(
            "SELECT m FROM ModelEvaluation m ORDER BY m.evalDate DESC");
        return query.getResultList();
    }

    public List<ModelEvaluation> findByModelType(String modelType) {
        Query query = entityManager.createQuery(
            "SELECT m FROM ModelEvaluation m WHERE m.modelType = :modelType ORDER BY m.evalDate DESC");
        query.setParameter("modelType", modelType);
        return query.getResultList();
    }

    public ModelEvaluation findLatestByModelType(String modelType) {
        Query query = entityManager.createQuery(
            "SELECT m FROM ModelEvaluation m WHERE m.modelType = :modelType ORDER BY m.evalDate DESC");
        query.setParameter("modelType", modelType);
        query.setMaxResults(1);
        List results = query.getResultList();
        return results.isEmpty() ? null : (ModelEvaluation) results.get(0);
    }

    public List<ModelEvaluation> findRecentEvaluations(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Query query = entityManager.createQuery(
            "SELECT m FROM ModelEvaluation m WHERE m.evalDate >= :since ORDER BY m.evalDate DESC");
        query.setParameter("since", since);
        return query.getResultList();
    }

    public List<ModelEvaluation> findNeedingRetrain() {
        Query query = entityManager.createQuery(
            "SELECT m FROM ModelEvaluation m WHERE m.needRetrain = 1 ORDER BY m.evalDate DESC");
        return query.getResultList();
    }

    public ModelEvaluation update(ModelEvaluation evaluation) {
        return entityManager.merge(evaluation);
    }

    public void delete(ModelEvaluation evaluation) {
        entityManager.remove(evaluation);
    }
}
