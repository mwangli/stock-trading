package online.mwang.foundtrading.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 11:00
 * @description: StockInfo
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockInfo {
    private String name;
    private String code;
    private String market;
    private Double increase;
    private Double price;
    private String prices;
    private String increaseRate;
    private Double score;
    private Date createTime;
    private Date updateTime;
}
