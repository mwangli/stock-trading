package com.stock.databus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("stock_info")
public class StockInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String market;

    private Integer isSt;

    private Integer isTradable;

    private BigDecimal price;

    private BigDecimal increase;

    @TableField("`delete`")
    private String deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
