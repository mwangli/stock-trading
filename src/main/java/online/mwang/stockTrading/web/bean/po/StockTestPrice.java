package online.mwang.stockTrading.web.bean.po;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data

@Document
@CompoundIndex(def = "{'code':1,'date':1}", unique = true)
public class StockTestPrice {

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
