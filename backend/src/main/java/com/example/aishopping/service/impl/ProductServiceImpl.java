package com.example.aishopping.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aishopping.entity.Product;
import com.example.aishopping.mapper.ProductMapper;
import com.example.aishopping.service.ProductService;
import org.springframework.stereotype.Service;

/**
 * 商品服务实现类
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {
    
}
