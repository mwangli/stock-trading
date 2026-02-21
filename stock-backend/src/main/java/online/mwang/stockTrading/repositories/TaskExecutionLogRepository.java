package online.mwang.stockTrading.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import online.mwang.stockTrading.entities.TaskExecutionLog;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * V3.0 任务执行日志 Repository
 * 从MySQL读取Python写入的任务日志
 */
@Repository
@Transactional
public class TaskExecutionLogRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(TaskExecutionLog log) {
        entityManager.persist(log);
    }

    public TaskExecutionLog findById(Long id) {
        return entityManager.find(TaskExecutionLog.class, id);
    }

    public List<TaskExecutionLog> findByTaskType(String taskType) {
        Query query = entityManager.createQuery(
            "SELECT t FROM TaskExecutionLog t WHERE t.taskType = :taskType ORDER BY t.startTime DESC");
        query.setParameter("taskType", taskType);
        return query.getResultList();
    }

    public List<TaskExecutionLog> findByModuleId(String moduleId) {
        Query query = entityManager.createQuery(
            "SELECT t FROM TaskExecutionLog t WHERE t.moduleId = :moduleId ORDER BY t.startTime DESC");
        query.setParameter("moduleId", moduleId);
        return query.getResultList();
    }

    public List<TaskExecutionLog> findByStatus(String status) {
        Query query = entityManager.createQuery(
            "SELECT t FROM TaskExecutionLog t WHERE t.status = :status ORDER BY t.startTime DESC");
        query.setParameter("status", status);
        return query.getResultList();
    }

    public List<TaskExecutionLog> findRecent(int limit) {
        Query query = entityManager.createQuery(
            "SELECT t FROM TaskExecutionLog t ORDER BY t.startTime DESC");
        query.setMaxResults(limit);
        return query.getResultList();
    }

    public List<TaskExecutionLog> findByDateRange(Date startDate, Date endDate) {
        Query query = entityManager.createQuery(
            "SELECT t FROM TaskExecutionLog t WHERE t.startTime BETWEEN :startDate AND :endDate ORDER BY t.startTime DESC");
        query.setParameter("startDate", startDate);
        query.setParameter("endDate", endDate);
        return query.getResultList();
    }

    public TaskExecutionLog update(TaskExecutionLog log) {
        return entityManager.merge(log);
    }

    public void delete(TaskExecutionLog log) {
        entityManager.remove(log);
    }
}
