package online.mwang.stockTrading.web.bean.po;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
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
@AllArgsConstructor
@NoArgsConstructor
public class StockPrices {
    @Id
    private String id;
    private String code;
    private String name;
    private String date;
    private Double price1;
    private Double price2;
    private Double price3;
    private Double price4;
    private Double increaseRate;

    public StockPrices(Double price1) {
        this.price1 = price1;
    }

    public StockPrices(String code, String name, String date, Double price1) {
        this.code = code;
        this.name = name;
        this.date = date;
        this.price1 = price1;
    }

    public StockPrices(String code, String name, String date, Double price1, Double price2, Double price3, Double price4) {
        this.code = code;
        this.name = name;
        this.date = date;
        this.price1 = price1;
        this.price2 = price2;
        this.price3 = price3;
        this.price4 = price4;
    }
}
