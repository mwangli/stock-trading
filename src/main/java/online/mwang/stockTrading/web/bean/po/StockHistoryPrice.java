package online.mwang.stockTrading.web.bean.po;


import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
public class StockHistoryPrice {
    @Id
    private String id;
    private String date;
    private String name;
    private String code;
    private Double price1;
    private Double price2;
    private Double price3;
    private Double price4;
}
