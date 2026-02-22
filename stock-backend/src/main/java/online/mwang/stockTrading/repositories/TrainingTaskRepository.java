package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.TrainingTask;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 训练任务 Repository
 * 从MySQL读取Python写入的训练任务数据
 */
@Repository
@Transactional
public class TrainingTaskRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(TrainingTask task) {
        entityManager.persist(task);
    }

    public TrainingTask findById(Long id) {
        return entityManager.find(TrainingTask.class, id);
    }

    public TrainingTask findByTaskId(String taskId) {
        Query query = entityManager.createQuery(
            "SELECT t FROM TrainingTask t WHERE t.taskId = :taskId");
        query.setParameter("taskId", taskId);
        query.setMaxResults(1);
        List results = query.getResultList();
        return results.isEmpty() ? null : (TrainingTask) results.get(0);
    }

    public List<TrainingTask> findAll() {
        Query query = entityManager.createQuery(
            "SELECT t FROM TrainingTask t ORDER BY t.createTime DESC");
        return query.getResultList();
    }

    public List<TrainingTask> findByStatus(String status) {
        Query query = entityManager.createQuery(
            "SELECT t FROM TrainingTask t WHERE t.status = :status ORDER BY t.createTime DESC");
        query.setParameter("status", status);
        return query.getResultList();
    }

    public List<TrainingTask> findRecentTasks(int limit) {
        Query query = entityManager.createQuery(
            "SELECT t FROM TrainingTask t ORDER BY t.createTime DESC");
        query.setMaxResults(limit);
        return query.getResultList();
    }

    public TrainingTask update(TrainingTask task) {
        return entityManager.merge(task);
    }

    public void delete(TrainingTask task) {
        entityManager.remove(task);
    }
}
