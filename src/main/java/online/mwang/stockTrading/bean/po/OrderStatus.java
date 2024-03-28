package online.mwang.stockTrading.bean.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatus {
    private String answerNo;
    private String code;
    private String name;
    private String status;
}
