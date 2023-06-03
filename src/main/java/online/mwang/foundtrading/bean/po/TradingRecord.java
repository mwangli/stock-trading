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
public class TradingRecord {

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

    private Long strategyId;

    private String strategyName;

    public static SFunction<TradingRecord, Object> getOrder(String key) {
        if (key == null) return TradingRecord::getUpdateTime;
        switch (key) {
            case "buyDate":
                return TradingRecord::getBuyDate;
            case "buyPrice":
                return TradingRecord::getBuyPrice;
            case "buyAmount":
                return TradingRecord::getBuyAmount;
            case "saleDate":
                return TradingRecord::getSaleDate;
            case "salePrice":
                return TradingRecord::getSalePrice;
            case "saleAmount":
                return TradingRecord::getSaleAmount;
            case "income":
                return TradingRecord::getIncome;
            case "sold":
                return TradingRecord::getSold;
            case "holdDays":
                return TradingRecord::getHoldDays;
            case "dailyIncomeRate":
                return TradingRecord::getDailyIncomeRate;
            default:
                return TradingRecord::getUpdateTime;
        }
    }
}
