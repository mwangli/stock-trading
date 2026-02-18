package online.mwang.stockTrading.core.exception;

import online.mwang.stockTrading.core.dto.Response;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class BusinessExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Response<?> bindException(BusinessException exception) {
        return Response.fail(20010, exception.getMessage());
    }
}
