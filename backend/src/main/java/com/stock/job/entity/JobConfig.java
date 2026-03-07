package com.stock.job.entity;

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

    /**
     * 状态：1-启用，0-禁用
     */
    @Column(nullable = false)
    private Integer status;

    private String description;

    @Column(nullable = false)
    private String beanName;

    @Column(nullable = false)
    private String methodName;

    private String params;

    /**
     * 上次运行时间
     */
    private LocalDateTime lastRunTime;

    /**
     * 上次运行耗时(ms)
     */
    private Long lastCostTime;

    /**
     * 上次运行状态：1-成功，0-失败
     */
    private Integer lastStatus;

    /**
     * 错误信息
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;
}
