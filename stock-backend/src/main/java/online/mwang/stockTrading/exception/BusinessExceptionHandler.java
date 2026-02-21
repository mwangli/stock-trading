package online.mwang.stockTrading.exception;

import online.mwang.stockTrading.dto.Response;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class BusinessExceptionHandler {

    @ExceptionHandler(online.mwang.stockTrading.core.exception.BusinessException.class)
    public Response<?> bindException(online.mwang.stockTrading.core.exception.BusinessException exception) {
        return Response.fail(20010, exception.getMessage());
    }
}
