package online.mwang.stockTrading.web.mapper;

import online.mwang.stockTrading.web.bean.po.StockInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface StockInfoMapper {
    List<StockInfo> findAll();
    StockInfo findByCode(String code);
    void save(StockInfo stockInfo);
    void update(StockInfo stockInfo);
    void delete(String code);
}
