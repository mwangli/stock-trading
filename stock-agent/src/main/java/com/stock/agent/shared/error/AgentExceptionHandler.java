// AI_GENERATE_START --
package com.stock.agent.shared.error;

import com.stock.agent.shared.api.ResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Agent 对外接口统一异常转换器。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Slf4j
@RestControllerAdvice
public class AgentExceptionHandler {

    /**
     * 将 Backend 权威事实不可用转换为稳定错误码，禁止调用方误判为空数据。
     *
     * @param exception 事实源不可用异常
     * @return HTTP 503 和统一失败响应
     */
    @ExceptionHandler(FactSourceUnavailableException.class)
    public ResponseEntity<ResponseDTO<Void>> handleFactSourceUnavailable(
            FactSourceUnavailableException exception) {
        log.warn("[Agent] Backend 权威事实源不可用: errorCode={}, causeType={}",
                FactSourceUnavailableException.ERROR_CODE, exception.getClass().getSimpleName());
        String message = FactSourceUnavailableException.ERROR_CODE + ": " + exception.getMessage();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ResponseDTO.error(message));
    }
}
// AI_GENERATE_END -
