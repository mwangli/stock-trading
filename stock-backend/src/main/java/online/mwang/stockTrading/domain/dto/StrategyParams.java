package online.mwang.stockTrading.web.bean.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StrategyParams {
    private Double preRateFactor;
    private Integer priceTolerance;
    private Integer historyLimit;
}
