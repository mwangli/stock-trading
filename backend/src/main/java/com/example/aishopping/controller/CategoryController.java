package com.example.aishopping.controller;

import com.example.aishopping.entity.Category;
import com.example.aishopping.mapper.CategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品分类控制器
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired
    private CategoryMapper categoryMapper;

    /**
     * 获取所有分类
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<Category> categories = categoryMapper.selectTopCategories();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", categories);

        return ResponseEntity.ok(response);
    }

    /**
     * 获取分类详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        Category category = categoryMapper.selectById(id);

        Map<String, Object> response = new HashMap<>();
        if (category != null) {
            response.put("success", true);
            response.put("data", category);
        } else {
            response.put("success", false);
            response.put("message", "分类不存在");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 获取子分类
     */
    @GetMapping("/{id}/children")
    public ResponseEntity<Map<String, Object>> getChildren(@PathVariable Long id) {
        List<Category> children = categoryMapper.selectByParentId(id);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", children);

        return ResponseEntity.ok(response);
    }
}
