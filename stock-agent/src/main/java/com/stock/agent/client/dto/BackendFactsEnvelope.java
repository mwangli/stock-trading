// AI_GENERATE_START -
package com.stock.agent.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Backend 内部事实统一响应载体。
 *
 * @param success 是否成功
 * @param message 响应说明
 * @param data 结构化业务数据
 * @author mwangli
 * @since 2026-10-23
 */
public record BackendFactsEnvelope(boolean success, String message, JsonNode data) {
}
// AI_GENERATE_END -
