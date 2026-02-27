package com.example.aishopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aishopping.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商品分类Mapper接口
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 根据父分类ID查询子分类
     */
    @Select("SELECT * FROM categories WHERE parent_id = #{parentId} ORDER BY level, name")
    List<Category> selectByParentId(@Param("parentId") Long parentId);

    /**
     * 查询所有顶级分类
     */
    @Select("SELECT * FROM categories WHERE parent_id IS NULL ORDER BY name")
    List<Category> selectTopCategories();

    /**
     * 根据名称查询分类
     */
    @Select("SELECT * FROM categories WHERE name = #{name} LIMIT 1")
    Category selectByName(@Param("name") String name);
}
