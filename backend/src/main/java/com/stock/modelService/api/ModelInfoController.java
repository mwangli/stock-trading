package com.stock.modelService.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 模型信息控制器
 * 对应前端 /api/modelInfo/* 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/modelInfo")
public class ModelInfoController {

    /**
     * 获取模型列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listModels(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code) {

        List<Map<String, Object>> models = new ArrayList<>();

        // Mock数据
        for (int i = 0; i < 5; i++) {
            Map<String, Object> model = new HashMap<>();
            String stockCode = i % 2 == 0 ? "600519" : "000858";
            model.put("key", "m" + i);
            model.put("code", stockCode);
            model.put("name", i % 2 == 0 ? "贵州茅台" : "五粮液");
            model.put("paramsSize", "100k");
            model.put("trainTimes", 10 + i);
            model.put("trainPeriod", "2h");
            model.put("testDeviation", 0.0123 + i * 0.001);
            model.put("score", 95.5 - i);
            model.put("updateTime", LocalDateTime.now().minusDays(i));
            model.put("status", 1); // 1: Success
            models.add(model);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", models);
        response.put("total", models.size());
        response.put("success", true);
        response.put("current", current);
        response.put("pageSize", pageSize);

        return ResponseEntity.ok(response);
    }

    /**
     * 获取测试数据
     */
    @GetMapping("/listTestData")
    public ResponseEntity<Map<String, Object>> listTestData(@RequestParam String code) {
        Map<String, Object> data = generateMockPoints();
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取验证数据
     */
    @GetMapping("/listValidateData")
    public ResponseEntity<Map<String, Object>> listValidateData(@RequestParam String code) {
        Map<String, Object> data = generateMockPoints();
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> generateMockPoints() {
        List<Map<String, Object>> points = new ArrayList<>();
        Random random = new Random();
        double price = 100.0;
        double max = 0;
        double min = 10000;

        for (int i = 0; i < 50; i++) {
            double change = (random.nextDouble() - 0.5) * 5;
            price += change;

            // 真实值
            Map<String, Object> p1 = new HashMap<>();
            p1.put("x", LocalDate.now().minusDays(50 - i).toString());
            p1.put("y", price);
            p1.put("type", "真实值");
            points.add(p1);

            // 预测值
            Map<String, Object> p2 = new HashMap<>();
            p2.put("x", LocalDate.now().minusDays(50 - i).toString());
            p2.put("y", price + (random.nextDouble() - 0.5) * 2);
            p2.put("type", "预测值");
            points.add(p2);

            max = Math.max(max, price);
            min = Math.min(min, price);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("points", points);
        result.put("maxValue", max + 10);
        result.put("minValue", min - 10);
        return result;
    }
}
