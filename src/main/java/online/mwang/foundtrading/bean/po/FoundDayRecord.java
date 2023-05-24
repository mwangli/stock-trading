package online.mwang.foundtrading.bean.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.Date;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 11:00
 * @description: FoundDayAmount
 */
@Data
public class FoundDayRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String accountType;

    private String buyDate;

    private Double buyNumber;

    private Double buyPrice;

    private Double buyAmount;

    private String todayDate;

    private Double todayPrice;

    private Double todayAmount;

    private Double expectedIncome;

    private Double dailyIncomeRate;

    private Date createTime;

    private Date updateTime;
}
