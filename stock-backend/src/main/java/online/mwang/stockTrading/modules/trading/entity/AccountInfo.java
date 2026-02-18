package online.mwang.stockTrading.modules.trading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 账户信息实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Double availableAmount;

    private Double usedAmount;

    private Double totalAmount;

    private Date createTime;

    private Date updateTime;
}
