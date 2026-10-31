// AI_GENERATE_START -
package com.stock.console.operations;

import com.stock.console.web.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operations")
public class OperationsController {

    private final OperationsService operations;

    public OperationsController(OperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(operations.status());
    }

    @GetMapping("/logs")
    public ApiResponse<List<String>> logs(@RequestParam(defaultValue = "500") int lines) {
        return ApiResponse.ok(operations.tail(lines));
    }

    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return operations.stream();
    }
}
// AI_GENERATE_END -
