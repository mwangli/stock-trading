package online.mwang.stockTrading.web.bean.base;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @version 1.0.0
 * @authori
 * @date7
 * @descriptione
 */
@Data
@AllArgsConstructor
public class Response<T> {
    private T data;
    private Boolean success;
    private Integer errorCode;
    private String errorMessage;
    private Long total;


    public static <T> Response<T> success() {
        return success(null);
    }

    public static <T> Response<T> success(T data) {
        return success(data, null);
    }

    public static <T> Response<T> success(T data, Long total) {
        return new Response<>(data, true, null, null, total);
    }

    public static <T> Response<T> fail() {
        return fail(null);
    }

    public static <T> Response<T> fail(Integer errorCode) {
        return fail(errorCode, null);
    }

    public static <T> Response<T> fail(Integer errorCode, String errorMessage) {
        return new Response<>(null, false, errorCode, errorMessage, null);
    }


}
