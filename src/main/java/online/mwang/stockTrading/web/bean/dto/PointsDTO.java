package online.mwang.stockTrading.web.bean.dto;

import lombok.Data;
import online.mwang.stockTrading.web.bean.vo.Point;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2024/4/28 15:19
 * @description: StockPricesDTO
 */
@Data
public class PointsDTO {
    List<Point> points;
    Double maxValue;
    Double minValue;

    public PointsDTO(List<Point> points) {
        this.points = points;
        this.maxValue = Math.ceil(points.stream().mapToDouble(Point::getY).max().orElse(0.0));
        this.minValue = Math.floor(points.stream().mapToDouble(Point::getY).min().orElse(0.0));
    }
}
