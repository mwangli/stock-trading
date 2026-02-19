package online.mwang.stockTrading.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 账户信息实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "account_info")
public class AccountInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "available_amount")
    private Double availableAmount;

    @Column(name = "used_amount")
    private Double usedAmount;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;

    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
}
