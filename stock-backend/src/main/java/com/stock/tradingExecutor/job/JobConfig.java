package com.stock.tradingExecutor.job;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "job_config")
@Accessors(chain = true)
@EntityListeners(AuditingEntityListener.class)
public class JobConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String jobName;

    @Column(nullable = false)
    private String cronExpression;

    @Column(nullable = false)
    private Integer status;

    private String description;

    @Column(nullable = false)
    private String beanName;

    @Column(nullable = false)
    private String methodName;

    private String params;

    private LocalDateTime lastRunTime;
    private Long lastCostTime;
    private Integer lastStatus;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;
}
