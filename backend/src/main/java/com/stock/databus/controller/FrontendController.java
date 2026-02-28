package com.stock.databus.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 前端页面控制器
 * 处理所有前端页面的请求
 */
@Controller
@RequestMapping("/")
public class FrontendController {

    /**
     * 处理所有非 API 请求，返回前端页面
     * 支持 React Router 的 history 模式
     */
    @GetMapping(value = {"/", "/{path:[^api]*}"})
    public String index() {
        return "forward:/index.html";
    }
}