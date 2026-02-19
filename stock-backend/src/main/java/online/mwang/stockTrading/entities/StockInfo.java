package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 股票信息实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "stock_info")
public class StockInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "code", unique = true, nullable = false)
    private String code;

    @Column(name = "market")
    private String market;

    @Column(name = "industry")
    private String industry;

    @Column(name = "listing_date")
    private LocalDate listingDate;

    @Column(name = "is_st")
    private Boolean isSt;

    @Column(name = "is_tradable")
    private Boolean isTradable;

    @Column(name = "increase")
    private Double increase;

    @Column(name = "price")
    private Double price;

    @Column(name = "predict_price")
    private Double predictPrice;

    @Column(name = "score")
    private Double score;

    @Column(name = "permission")
    private String permission;

    @Column(name = "buy_sale_count")
    private Integer buySaleCount;

    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;

    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;

    @Column(name = "deleted")
    private String deleted;

    @Column(name = "selected")
    private String selected;

    @Transient
    private List<StockPrices> pricesList;

    @Transient
    private Double maxPrice;

    @Transient
    private Double minPrice;

    @Transient
    private List<StockPrices> increaseRateList;

    @Transient
    private Double maxIncrease;

    @Transient
    private Double minIncrease;
}
