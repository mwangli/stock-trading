package online.mwang.stockTrading.model.component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author 13255
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockData {
    private String date;
    private String code;
    private String name;
    private double open;
    private double close;
    private double low;
    private double high;
}
