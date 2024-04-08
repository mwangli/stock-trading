package online.mwang.stockTrading.model.data;

import lombok.Data;

@Data
public class StockData {
    private String code; // date
    private String name; // stock name
    private String date; // stock name
    private double price1; // open price
    private double price2; // close price
    private double price3; // low price
    private double price4; // high price
}
