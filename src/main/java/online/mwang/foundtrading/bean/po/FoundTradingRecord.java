package online.mwang.foundtrading.bean.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.Date;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 11:00
 * @description: FoundTradingRecord
 */
@Data
public class FoundTradingRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String accountType;

    private Double buyAmount;

    private Double buyPrice;

    private Double buyNumber;

    private Date buyDate;

    private Double saleAmount;

    private Double salePrice;

    private Double saleNumber;

    private Date saleDate;

    private Double realIncome;

    private Double realIncomeRate;

    private String sold;

    private Date createTime;

    private Date updateTime;
}
