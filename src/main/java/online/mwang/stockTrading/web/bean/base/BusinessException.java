package online.mwang.stockTrading.web.bean.base;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/7/3 14:12
 * @description: BusinessException
 */
@Data
@AllArgsConstructor
public class BusinessException extends RuntimeException{
    private String message;
}
