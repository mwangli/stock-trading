package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * V3.0 任务执行日志实体
 * Python服务写入，Java服务仅读取
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "task_execution_log")
public class TaskExecutionLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "task_name")
    private String taskName;
    
    @Column(name = "task_type")
    private String taskType;
    
    @Column(name = "module_id")
    private String moduleId;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "start_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date startTime;
    
    @Column(name = "end_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date endTime;
    
    @Column(name = "duration")
    private Integer duration;
    
    @Column(name = "records_processed")
    private Integer recordsProcessed;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "execute_info", columnDefinition = "JSON")
    private String executeInfo;
    
    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;
}
