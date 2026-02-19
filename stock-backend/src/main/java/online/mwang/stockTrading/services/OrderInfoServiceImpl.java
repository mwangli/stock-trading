package online.mwang.stockTrading.services;

import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.entities.OrderInfo;
import online.mwang.stockTrading.repositories.OrderInfoRepository;
import online.mwang.stockTrading.services.OrderInfoService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单服务实现
 */
@Service
@RequiredArgsConstructor
public class OrderInfoServiceImpl implements OrderInfoService {

    private final OrderInfoRepository orderInfoRepository;

    @Override
    public void save(OrderInfo orderInfo) {
        orderInfoRepository.save(orderInfo);
    }

    @Override
    public OrderInfo findById(Long id) {
        return orderInfoRepository.findById(id);
    }

    @Override
    public OrderInfo update(OrderInfo orderInfo) {
        return orderInfoRepository.update(orderInfo);
    }

    @Override
    public void delete(OrderInfo orderInfo) {
        orderInfoRepository.delete(orderInfo);
    }

    @Override
    public List<OrderInfo> findAll() {
        return orderInfoRepository.findAll();
    }

    @Override
    public List<String> listAnswerNo() {
        return orderInfoRepository.listAnswerNo();
    }

    @Override
    public Long count() {
        return orderInfoRepository.count();
    }
}
