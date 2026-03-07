package com.stock.dataCollector.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 规则管理控制器 (Mock)
 * 对应前端 /api/rule 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/rule")
public class RuleController {

    /**
     * 获取规则列表 (虽然前端主要用 /api/job/list，但这里保留以防万一)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listRules(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("data", new java.util.ArrayList<>());
        response.put("total", 0);
        response.put("success", true);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 新建规则
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> addRule(@RequestBody Map<String, Object> params) {
        log.info("新建规则: {}", params);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 删除规则
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> removeRule(@RequestBody(required = false) Map<String, Object> params) {
        log.info("删除规则: {}", params);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 更新规则
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateRule(@RequestBody Map<String, Object> params) {
        log.info("更新规则: {}", params);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
