package online.mwang.stockTrading.services;

import online.mwang.stockTrading.entities.OrderInfo;

import java.util.List;

/**
 * 订单服务接口
 */
public interface OrderInfoService {

    void save(OrderInfo orderInfo);

    OrderInfo findById(Long id);

    OrderInfo update(OrderInfo orderInfo);

    void delete(OrderInfo orderInfo);

    List<OrderInfo> findAll();

    List<String> listAnswerNo();

    Long count();
}
