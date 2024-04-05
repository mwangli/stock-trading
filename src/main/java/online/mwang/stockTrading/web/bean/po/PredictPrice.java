package online.mwang.stockTrading.web.bean.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredictPrice {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String stockCode;
    private String date;
    private Double actualPrice;
    private Double predictPrice;
    private Date createTime;
    private Date updateTime;
}
