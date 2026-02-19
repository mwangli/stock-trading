package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

/**
 * 交易记录实体
 */
@Data
@Entity
@Table(name = "trading_record")
public class TradingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "buy_date")
    @Temporal(TemporalType.DATE)
    private Date buyDate;

    @Column(name = "buy_date_string")
    private String buyDateString;

    @Column(name = "buy_no")
    private String buyNo;

    @Column(name = "buy_price")
    private Double buyPrice;

    @Column(name = "buy_number")
    private Double buyNumber;

    @Column(name = "buy_amount")
    private Double buyAmount;

    @Column(name = "sale_date")
    @Temporal(TemporalType.DATE)
    private Date saleDate;

    @Column(name = "sale_date_string")
    private String saleDateString;

    @Column(name = "sale_no")
    private String saleNo;

    @Column(name = "sale_price")
    private Double salePrice;

    @Column(name = "sale_number")
    private Double saleNumber;

    @Column(name = "sale_amount")
    private Double saleAmount;

    @Column(name = "income")
    private Double income;

    @Column(name = "income_rate")
    private Double incomeRate;

    @Column(name = "hold_days")
    private Integer holdDays;

    @Column(name = "daily_income_rate")
    private Double dailyIncomeRate;

    @Column(name = "sold")
    private String sold;

    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;

    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
}
