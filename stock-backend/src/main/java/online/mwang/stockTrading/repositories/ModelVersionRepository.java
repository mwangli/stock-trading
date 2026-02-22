package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.ModelVersion;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模型版本 Repository
 * 从MySQL读取Python写入的模型版本数据
 */
@Repository
@Transactional
public class ModelVersionRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(ModelVersion version) {
        entityManager.persist(version);
    }

    public ModelVersion findById(Long id) {
        return entityManager.find(ModelVersion.class, id);
    }

    public List<ModelVersion> findAll() {
        Query query = entityManager.createQuery(
            "SELECT m FROM ModelVersion m ORDER BY m.trainDate DESC");
        return query.getResultList();
    }

    public List<ModelVersion> findByModelType(String modelType) {
        Query query = entityManager.createQuery(
            "SELECT m FROM ModelVersion m WHERE m.modelType = :modelType ORDER BY m.trainDate DESC");
        query.setParameter("modelType", modelType);
        return query.getResultList();
    }

    public ModelVersion findActiveVersion(String modelType) {
        Query query = entityManager.createQuery(
            "SELECT m FROM ModelVersion m WHERE m.modelType = :modelType AND m.isActive = 1");
        query.setParameter("modelType", modelType);
        query.setMaxResults(1);
        List results = query.getResultList();
        return results.isEmpty() ? null : (ModelVersion) results.get(0);
    }

    public List<ModelVersion> findActiveVersions() {
        Query query = entityManager.createQuery(
            "SELECT m FROM ModelVersion m WHERE m.isActive = 1");
        return query.getResultList();
    }

    public ModelVersion update(ModelVersion version) {
        return entityManager.merge(version);
    }

    public void setInactive(String modelType) {
        Query query = entityManager.createQuery(
            "UPDATE ModelVersion m SET m.isActive = 0 WHERE m.modelType = :modelType");
        query.setParameter("modelType", modelType);
        query.executeUpdate();
    }

    public void delete(ModelVersion version) {
        entityManager.remove(version);
    }
}
