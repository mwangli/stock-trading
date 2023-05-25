package online.mwang.foundtrading.bean.base;

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
    private Boolean success;
    private T data;
    private Integer errorCode;
    private String errorMessage;
    private Integer showType;
    private Long total;


    public static <T> Response<T> success() {
        return new Response<>(true, null, 0, "success", 0,0L);
    }

    public static <T> Response<T> success(T data) {
        return new Response<>(true, data, 0, "success", 0, 0L);
    }

    public static <T> Response<T> success(T data, long total) {
        return new Response<>(true, data, 0, "success", 0, total);
    }

    public static <T> Response<T> fail(Integer errorCode, String errorMessage) {
        return new Response<>(false, null, errorCode, errorMessage, 2, 0L);
    }
}
