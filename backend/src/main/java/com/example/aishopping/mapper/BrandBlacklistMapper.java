package com.example.aishopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aishopping.entity.BrandBlacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 品牌黑名单Mapper接口
 */
@Mapper
public interface BrandBlacklistMapper extends BaseMapper<BrandBlacklist> {

    /**
     * 查询所有生效的黑名单
     */
    @Select("SELECT * FROM brand_blacklist WHERE is_active = TRUE ORDER BY brand_name")
    List<BrandBlacklist> selectActiveBlacklist();

    /**
     * 根据品牌名称查询
     */
    @Select("SELECT * FROM brand_blacklist WHERE brand_name = #{brandName} LIMIT 1")
    BrandBlacklist selectByBrandName(@Param("brandName") String brandName);

    /**
     * 检查品牌是否在黑名单中
     */
    @Select("SELECT COUNT(*) FROM brand_blacklist WHERE brand_name = #{brandName} AND is_active = TRUE")
    int isBrandBlacklisted(@Param("brandName") String brandName);
}
