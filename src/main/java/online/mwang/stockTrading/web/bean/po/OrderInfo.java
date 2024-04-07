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
public class OrderInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String answerNo;
    private String code;
    private String name;
    private String status;
    private String date;
    private String time;
    private String type;
    private Double number;
    private Double price;
    private Date createTime;
    private Date updateTime;
}
