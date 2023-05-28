package online.mwang.foundtrading.bean.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.Data;

import java.util.Date;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 11:00
 * @description: FoundTradingRecord
 */
@Data
public class FoundTradingRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private Date buyDate;

    private String buyDateString;

    private String buyNo;

    private Double buyPrice;

    private Double buyNumber;

    private Double buyAmount;

    private Date saleDate;

    private String saleDateString;

    private String saleNo;

    private Double salePrice;

    private Double saleNumber;

    private Double saleAmount;

    private Double income;

    private Double incomeRate;

    private Integer holdDays;

    private Double dailyIncomeRate;

    private String sold;

    private Date createTime;

    private Date updateTime;

    public static SFunction<FoundTradingRecord, Object> getOrder(String key) {
        if (key == null) return FoundTradingRecord::getUpdateTime;
        switch (key) {
            case "buyDate":
                return FoundTradingRecord::getBuyDate;
            case "buyPrice":
                return FoundTradingRecord::getBuyPrice;
            case "buyAmount":
                return FoundTradingRecord::getBuyAmount;
            case "saleDate":
                return FoundTradingRecord::getSaleDate;
            case "salePrice":
                return FoundTradingRecord::getSalePrice;
            case "saleAmount":
                return FoundTradingRecord::getSaleAmount;
            case "income":
                return FoundTradingRecord::getIncome;
            case "sold":
                return FoundTradingRecord::getSold;
            case "holdDays":
                return FoundTradingRecord::getHoldDays;
            case "dailyIncomeRate":
                return FoundTradingRecord::getDailyIncomeRate;
            default:
                return FoundTradingRecord::getUpdateTime;
        }
    }
}
