package com.example.aishopping.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 品牌黑名单实体类
 */
@Data
@TableName("brand_blacklist")
public class BrandBlacklist {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("brand_name")
    private String brandName;

    @TableField("brand_type")
    private String brandType;

    @TableField("reason")
    private String reason;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
