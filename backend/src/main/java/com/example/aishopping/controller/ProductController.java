package com.example.aishopping.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aishopping.entity.Product;
import com.example.aishopping.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 商品控制器
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 获取商品列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String keyword) {
        
        Page<Product> pageParam = new Page<>(page, size);
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        
        if (category != null && !category.isEmpty()) {
            wrapper.eq("main_category", category);
        }
        if (brand != null && !brand.isEmpty()) {
            wrapper.eq("brand", brand);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("product_title", keyword);
        }
        
        wrapper.orderByDesc("collected_at");
        Page<Product> result = productService.page(pageParam, wrapper);
        
        Map<String, Object> data = new HashMap<>();
        data.put("content", result.getRecords());
        data.put("totalElements", result.getTotal());
        data.put("totalPages", result.getPages());
        data.put("size", result.getSize());
        data.put("number", result.getCurrent());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取商品详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        Product product = productService.getById(id);
        
        Map<String, Object> response = new HashMap<>();
        if (product != null) {
            response.put("success", true);
            response.put("data", product);
        } else {
            response.put("success", false);
            response.put("message", "商品不存在");
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有分类
     */
    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getCategories() {
        List<String> categories = Arrays.asList("Electronics", "Cellphones", "Laptops", "Cameras", "Audio", "Gaming", "Wearables");
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", categories);
        
        return ResponseEntity.ok(response);
    }
}
