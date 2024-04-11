package online.mwang.stockTrading.web.bean.po;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.Date;

@Data
public class PredictPrice {

    @Id
    private String id;
    private String stockCode;
    private String date;
    private Double actualPrice1;
    private Double predictPrice1;
    private Double actualPrice2;
    private Double predictPrice2;
    private Date createTime;
    private Date updateTime;
}
