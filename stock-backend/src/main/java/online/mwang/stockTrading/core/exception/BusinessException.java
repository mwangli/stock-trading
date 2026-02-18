package online.mwang.stockTrading.core.exception;

/**
 * 业务异常
 */
public class BusinessException extends RuntimeException {
    
    private String message;
    
    public BusinessException(String message) {
        this.message = message;
    }
    
    public BusinessException(String message, Throwable cause) {
        super(cause);
        this.message = message;
    }
    
    @Override
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}
