package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 委托订单实体
 * 对应表: trade_orders
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "trade_orders")
public class TradeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", unique = true, nullable = false)
    private String orderId;

    @Column(name = "stock_code", nullable = false)
    private String stockCode;

    @Column(name = "stock_name")
    private String stockName;

    @Column(name = "order_type", nullable = false)
    private String orderType;  // BUY/SELL

    @Column(name = "order_side")
    private String orderSide; // OPEN/CLOSE

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "filled_quantity")
    private Integer filledQuantity;

    @Column(name = "filled_price")
    private BigDecimal filledPrice;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "commission")
    private BigDecimal commission;

    @Column(name = "status", nullable = false)
    private String status; // CREATE/SUBMIT/PENDING/FILLED/PARTIAL/CANCELLED/FAILED

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "order_time")
    private LocalTime orderTime;

    @Column(name = "cancel_time")
    private LocalTime cancelTime;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
