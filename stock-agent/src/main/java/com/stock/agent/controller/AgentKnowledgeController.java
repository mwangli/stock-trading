// AI_GENERATE_START----
package com.stock.agent.controller;

import com.stock.agent.dto.CorrectMemoryRequest;
import com.stock.agent.dto.CreateMemoryRequest;
import com.stock.agent.dto.MemoryResponse;
import com.stock.agent.dto.RagIngestRequest;
import com.stock.agent.dto.RagResult;
import com.stock.agent.dto.RagSearchRequest;
import com.stock.agent.rag.service.AgentRagService;
import com.stock.agent.service.AgentMemoryService;
import com.stock.agent.api.ResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent Memory 与 RAG 知识控制器。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentKnowledgeController {

    private final AgentMemoryService memoryService;
    private final AgentRagService ragService;
    private final AgentRequestUserResolver requestUserResolver;

    /**
     * 创建当前用户的 Memory。
     *
     * @param request 创建请求
     * @param httpRequest HTTP 请求
     * @return Memory 信息
     */
    @PostMapping("/memories")
    public ResponseDTO<MemoryResponse> createMemory(@RequestBody CreateMemoryRequest request,
                                                     HttpServletRequest httpRequest) {
        log.info("[Agent] 创建当前用户 Memory");
        return ResponseDTO.success(memoryService.create(
                request, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 查询当前用户的 Memory。
     *
     * @param httpRequest HTTP 请求
     * @return Memory 列表
     */
    @GetMapping("/memories")
    public ResponseDTO<List<MemoryResponse>> listMemories(HttpServletRequest httpRequest) {
        log.info("[Agent] 查询当前用户 Memory");
        return ResponseDTO.success(memoryService.list(requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 删除当前用户的 Memory。
     *
     * @param memoryId Memory 标识
     * @param httpRequest HTTP 请求
     * @return 空业务数据
     */
    @DeleteMapping("/memories/{memoryId}")
    public ResponseDTO<Void> forgetMemory(@PathVariable String memoryId,
                                          HttpServletRequest httpRequest) {
        log.info("[Agent] 删除 Memory: memoryId={}", memoryId);
        memoryService.forget(memoryId, requestUserResolver.requireUserScope(httpRequest));
        return ResponseDTO.success(null);
    }

    /**
     * 修正当前用户的 Memory。
     *
     * @param memoryId Memory 标识
     * @param request 修正请求
     * @param httpRequest HTTP 请求
     * @return 修正后的 Memory
     */
    @PostMapping("/memories/correct/{memoryId}")
    public ResponseDTO<MemoryResponse> correctMemory(@PathVariable String memoryId,
                                                      @RequestBody CorrectMemoryRequest request,
                                                      HttpServletRequest httpRequest) {
        log.info("[Agent] 修正 Memory: memoryId={}", memoryId);
        return ResponseDTO.success(memoryService.correct(
                memoryId, request, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 写入当前用户范围的 RAG 文档。
     *
     * @param request 文档请求
     * @param httpRequest HTTP 请求
     * @return 写入的 Chunk 数量
     */
    @PostMapping("/rag/documents")
    public ResponseDTO<Integer> ingestRag(@RequestBody RagIngestRequest request,
                                          HttpServletRequest httpRequest) {
        log.info("[Agent] 写入 RAG 文档: sourceId={}", request == null ? null : request.getSourceId());
        return ResponseDTO.success(ragService.ingest(
                request, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 检索当前用户范围的 RAG 文档。
     *
     * @param request 检索请求
     * @param httpRequest HTTP 请求
     * @return RAG 检索结果
     */
    @PostMapping("/rag/search")
    public ResponseDTO<List<RagResult>> searchRag(@RequestBody RagSearchRequest request,
                                                   HttpServletRequest httpRequest) {
        log.info("[Agent] 检索 RAG 文档");
        return ResponseDTO.success(ragService.search(
                request, requestUserResolver.requireUserScope(httpRequest)));
    }
}
// AI_GENERATE_END----
