package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.ModelInfo;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模型信息 Repository (Hibernate原生)
 */
@Repository
@Transactional
public class ModelInfoRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(ModelInfo modelInfo) {
        entityManager.persist(modelInfo);
    }

    public ModelInfo findById(Long id) {
        return entityManager.find(ModelInfo.class, id);
    }

    public ModelInfo update(ModelInfo modelInfo) {
        return entityManager.merge(modelInfo);
    }

    public void delete(ModelInfo modelInfo) {
        entityManager.remove(modelInfo);
    }

    public List<ModelInfo> findAll() {
        Query query = entityManager.createQuery("SELECT m FROM ModelInfo m");
        return query.getResultList();
    }

    public void resetStatus() {
        Query query = entityManager.createQuery("UPDATE ModelInfo m SET m.status = '1' WHERE m.status = '0'");
        query.executeUpdate();
    }

    public Long count() {
        Query query = entityManager.createQuery("SELECT COUNT(m) FROM ModelInfo m");
        return (Long) query.getSingleResult();
    }
}
