package online.mwang.stockTrading.web.bean.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

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
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private String market;
    private Double increase;
    private Double price;
    private Double predictPrice;
    private Double score;
    private String permission;
    private Integer buySaleCount;
    private Date createTime;
    private Date updateTime;
    private String deleted;
    private String selected;

    @TableField(exist = false)
    private List<StockPrices> pricesList;
    @TableField(exist = false)
    private Double maxPrice;
    @TableField(exist = false)
    private Double minPrice;
    @TableField(exist = false)
    private List<StockPrices> increaseRateList;
    @TableField(exist = false)
    private Double maxIncrease;
    @TableField(exist = false)
    private Double minIncrease;

    public static SFunction<StockInfo, Object> getOrder(String key) {
        if (key == null) return StockInfo::getScore;
        switch (key) {
            case "market":
                return StockInfo::getMarket;
            case "increase":
                return StockInfo::getIncrease;
            case "price":
                return StockInfo::getPrice;
            case "permission":
                return StockInfo::getPermission;
            case "buySaleCount":
                return StockInfo::getBuySaleCount;
            default:
                return StockInfo::getScore;
        }
    }
}
