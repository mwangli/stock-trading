package com.example.aishopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aishopping.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商品Mapper接口
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 根据TSIN查询商品
     */
    @Select("SELECT * FROM products WHERE tsin = #{tsin} LIMIT 1")
    Product selectByTsin(@Param("tsin") String tsin);

    /**
     * 检查TSIN是否已存在
     */
    @Select("SELECT COUNT(*) FROM products WHERE tsin = #{tsin}")
    int countByTsin(@Param("tsin") String tsin);

    /**
     * 根据分类查询商品
     */
    @Select("SELECT * FROM products WHERE main_category = #{category} ORDER BY collected_at DESC")
    List<Product> selectByCategory(@Param("category") String category);

    /**
     * 根据品牌查询商品
     */
    @Select("SELECT * FROM products WHERE brand = #{brand} ORDER BY collected_at DESC")
    List<Product> selectByBrand(@Param("brand") String brand);
}
