package com.example.aishopping.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集日志实体类
 */
@Data
@TableName("collection_logs")
public class CollectionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("tsin")
    private String tsin;

    @TableField("product_title")
    private String productTitle;

    @TableField("product_url")
    private String productUrl;

    @TableField("status")
    private String status;

    @TableField("message")
    private String message;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("response_time_ms")
    private Integer responseTimeMs;

    @TableField("proxy_used")
    private String proxyUsed;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
