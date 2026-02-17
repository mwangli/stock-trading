package online.mwang.stockTrading.web.bean.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrderInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    @EqualsAndHashCode.Include
    private String answerNo;
    @EqualsAndHashCode.Include
    private String code;
    private String name;
    private String status;
    @EqualsAndHashCode.Include
    private String date;
    private String time;
    private String type;
    private Double number;
    private Double price;
    private Double amount;
    private Double peer;
    private Date createTime;
    private Date updateTime;
}
