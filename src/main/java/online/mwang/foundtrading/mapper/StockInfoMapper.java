package online.mwang.foundtrading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.foundtrading.bean.po.StockInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 11:13
 * @description: StockInfoMapper
 */
@Mapper
public interface StockInfoMapper extends BaseMapper<StockInfo> {

    @Select("select * from stock_info where code = #{code}")
    StockInfo selectByCode(String code);

    @Select("select id, code from stock_info")
    List<StockInfo> listIdAndCode();

    @Update("update stock_info set permission = '1'")
    void resetPermission();
}
