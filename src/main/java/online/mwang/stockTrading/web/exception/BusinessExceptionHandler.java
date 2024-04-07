package online.mwang.stockTrading.web.exception;


import online.mwang.stockTrading.web.bean.base.BusinessException;
import online.mwang.stockTrading.web.bean.base.Response;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BusinessExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Response bindException(BusinessException exception) {
        return Response.fail(20010, exception.getMessage());
    }

}
