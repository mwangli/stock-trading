package online.mwang.stockTrading.bean.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/26 09:36
 * @description: AccountInfo
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
