package online.mwang.foundtrading.bean.param;

import lombok.Data;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/21 16:46
 * @description: LoginParam
 */
@Data
public class LoginParam {

    private String username;
    private String password;
    private String token;
}
