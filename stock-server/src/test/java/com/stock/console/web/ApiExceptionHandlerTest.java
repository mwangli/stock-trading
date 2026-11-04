// AI_GENERATE_START -
package com.stock.console.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void returnsSafeBadRequestForValidationFailure() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("chatRequest", "question", "x".repeat(100), false, null, null, "too long")));

        var response = handler.validation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("问题不能为空且不能超过 20000 个字符", response.getBody().message());
        assertFalse(response.getBody().message().contains("xxxxx"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        var response = handler.failure(new RuntimeException("secret stack detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("服务暂时无法完成请求", response.getBody().message());
    }
}
// AI_GENERATE_END -
