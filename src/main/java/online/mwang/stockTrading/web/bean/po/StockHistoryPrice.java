package online.mwang.stockTrading.web.bean.po;


import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @author 13255
 */
@Data
@Document
@EqualsAndHashCode(exclude = {"id"})
@CompoundIndex(def = "{'code':1,'date':1}", unique = true)
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
