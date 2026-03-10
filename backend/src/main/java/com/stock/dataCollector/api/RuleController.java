package com.stock.dataCollector.api;

import com.stock.common.dto.ResponseDTO;
import com.stock.dataCollector.domain.dto.RuleAddRequestDto;
import com.stock.dataCollector.domain.dto.RuleDeleteRequestDto;
import com.stock.dataCollector.domain.dto.RuleListResponseDto;
import com.stock.dataCollector.domain.dto.RuleOperationResponseDto;
import com.stock.dataCollector.domain.dto.RuleUpdateRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

/**
 * 规则管理控制器 (Mock)
 *
 * 对应前端 /api/rule 接口，提供规则列表、新增、删除、更新等操作。
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@RestController
@RequestMapping("/api/rule")
public class RuleController {

    /**
     * 获取规则列表
     *
     * @param current  当前页，从 1 开始
     * @param pageSize 每页条数
     * @return 规则列表及分页信息
     */
    @GetMapping
    public ResponseDTO<RuleListResponseDto> listRules(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {

        log.info("分页查询规则列表: current={}, pageSize={}", current, pageSize);

        RuleListResponseDto response = RuleListResponseDto.builder()
                .data(Collections.emptyList())
                .total(0)
                .success(true)
                .build();

        return ResponseDTO.success(response);
    }

    /**
     * 新建规则
     *
     * @param request 请求体，包含规则名称、描述等
     * @return 操作结果
     */
    @PostMapping
    public ResponseDTO<RuleOperationResponseDto> addRule(
            @RequestBody(required = false) RuleAddRequestDto request) {

        log.info("新建规则: name={}", request != null ? request.getName() : null);

        RuleOperationResponseDto response = RuleOperationResponseDto.builder()
                .success(true)
                .message("新建成功")
                .build();
        return ResponseDTO.success(response);
    }

    /**
     * 删除规则
     *
     * @param request 请求体，包含规则 ID
     * @return 操作结果
     */
    @DeleteMapping
    public ResponseDTO<RuleOperationResponseDto> removeRule(
            @RequestBody(required = false) RuleDeleteRequestDto request) {

        log.info("删除规则: id={}", request != null ? request.getId() : null);

        RuleOperationResponseDto response = RuleOperationResponseDto.builder()
                .success(true)
                .message("删除成功")
                .build();
        return ResponseDTO.success(response);
    }

    /**
     * 更新规则
     *
     * @param request 请求体，包含规则 ID、名称、描述等
     * @return 操作结果
     */
    @PutMapping
    public ResponseDTO<RuleOperationResponseDto> updateRule(
            @RequestBody(required = false) RuleUpdateRequestDto request) {

        log.info("更新规则: id={}", request != null ? request.getId() : null);

        RuleOperationResponseDto response = RuleOperationResponseDto.builder()
                .success(true)
                .message("更新成功")
                .build();
        return ResponseDTO.success(response);
    }
}
