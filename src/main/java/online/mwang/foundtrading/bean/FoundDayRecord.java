package online.mwang.foundtrading.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.math.BigDecimal;

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

    private BigDecimal todayAmount;
}
