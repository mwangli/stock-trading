package online.mwang.foundtrading.bean.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderInfo {

    private String answerNo;
    private String code;
    private String name;
    private String status;

    private String date;
    private String time;
    private String type;
    private Double number;
    private Double price;

    public OrderInfo(String answerNo, String code, String name, String status) {
        this.answerNo = answerNo;
        this.code = code;
        this.name = name;
        this.status = status;
    }
}
