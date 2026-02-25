package com.example.aishopping.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体类
 */
@Data
@TableName("products")
public class Product {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("tsin")
    private String tsin;
    
    @TableField("product_url")
    private String productUrl;
    
    @TableField("main_category")
    private String mainCategory;
    
    @TableField("lowest_category")
    private String lowestCategory;
    
    @TableField("category_id")
    private Long categoryId;
    
    @TableField("product_title")
    private String productTitle;
    
    @TableField("subtitle")
    private String subtitle;
    
    @TableField("description")
    private String description;
    
    @TableField("whats_in_box")
    private String whatsInBox;
    
    @TableField("brand")
    private String brand;
    
    @TableField("warranty_type")
    private String warrantyType;
    
    @TableField("warranty_period")
    private Integer warrantyPeriod;
    
    @TableField("image_urls")
    private String imageUrls;
    
    @TableField("rating")
    private BigDecimal rating;
    
    @TableField("review_count")
    private Integer reviewCount;
    
    @TableField("price")
    private BigDecimal price;
    
    @TableField("currency")
    private String currency;
    
    @TableField("video_url")
    private String videoUrl;
    
    @TableField("collected_at")
    private LocalDateTime collectedAt;
    
    @TableField("task_id")
    private Long taskId;
    
    @TableField("is_filtered")
    private Boolean isFiltered;
    
    @TableField("filter_reason")
    private String filterReason;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
