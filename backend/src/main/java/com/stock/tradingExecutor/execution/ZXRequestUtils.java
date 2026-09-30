// AI_GENERATE_START ---------
package com.stock.tradingExecutor.execution;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 中信证券 API 请求工具类。
 * 封装 HTTP 请求、Token 管理以及算术验证码登录流程。
 *
 * @author mwangli
 * @since 2026-03-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZXRequestUtils {

    private static final String REQUEST_URL = "https://weixin.citicsinfo.com/reqxml";
    private final ArithmeticCaptchaSolver captchaSolver;
    private final ZXBrokerConfig brokerConfig;
    private final ZXBrokerTokenStore tokenStore;
    private final Object loginLock = new Object();

    /**
     * 构建券商通用请求参数。
     *
     * @param paramMap 业务参数
     * @return 补充通用字段后的参数
     */
    public Map<String, Object> buildParams(Map<String, Object> paramMap) {
        Map<String, Object> params = paramMap == null ? new HashMap<>() : paramMap;
        params.put("cfrom", "H5");
        params.put("tfrom", "PC");
        params.put("newindex", "1");
        params.put("MobileCode", brokerConfig.getMobileCode());
        params.put("intacttoserver", brokerConfig.getIntactToServer());
        params.put("reqno", System.currentTimeMillis());
        return params;
    }

    /**
     * 从 Redis 获取当前有效 Token。
     * 本方法只检查缓存，不触发登录，供状态查询使用。
     *
     * @return 有效 Token，不存在或过期时返回 null
     */
    public String getToken() {
        return tokenStore.getToken();
    }

    /**
     * 将券商 Token 写入 Redis。
     *
     * @param token 新 Token
     */
    public void setToken(String token) {
        if (token != null && !token.isBlank()) {
            tokenStore.saveToken(token);
        }
    }

    /**
     * 获取业务调用必须使用的 Token。
     * Redis 中不存在 Token 时自动调用验证码登录；登录流程最多尝试三次。
     *
     * @return 可用于券商业务接口的 Token
     * @throws IllegalStateException 券商未启用、登录三次失败或 Redis 不可用时抛出
     */
    public String requireToken() {
        if (!Boolean.TRUE.equals(brokerConfig.getEnabled())) {
            throw new IllegalStateException("中信证券接入未启用");
        }
        String cachedToken = getToken();
        if (cachedToken != null && !cachedToken.isBlank()) {
            return cachedToken;
        }

        synchronized (loginLock) {
            cachedToken = getToken();
            if (cachedToken != null && !cachedToken.isBlank()) {
                return cachedToken;
            }
            log.warn("[ZXBroker] Redis 中无可用 Token，开始验证码登录，最多尝试 {} 次",
                    brokerConfig.getCaptchaMaxRetries());
            String loginToken = loginConfiguredAccount();
            if (loginToken == null || loginToken.isBlank()) {
                log.error("[ZXBroker] 验证码登录连续 {} 次失败，无可用 Token，中断业务流程",
                        brokerConfig.getCaptchaMaxRetries());
                throw new IllegalStateException("券商登录连续三次失败，无可用 Token");
            }
            String storedToken = getToken();
            if (storedToken == null || storedToken.isBlank()) {
                log.error("[ZXBroker] 登录成功但 Redis 中未读取到 Token，中断业务流程");
                throw new IllegalStateException("券商 Token 未成功写入 Redis");
            }
            return storedToken;
        }
    }

    /**
     * 向券商标准地址发送请求。
     *
     * @param formParam 表单参数
     * @return 券商 JSON 响应
     */
    public JSONObject request(Map<String, Object> formParam) {
        return request(REQUEST_URL, formParam);
    }

    private JSONObject request(String url, Map<String, Object> formParam) {
        try {
            String response = HttpUtil.createPost(url).form(formParam).execute().body();
            JSONObject result = JSONObject.parseObject(response);
            String newToken = result.getString("TOKEN");
            if (newToken != null && !newToken.isBlank()) {
                setToken(newToken);
            }
            return result;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (JSONException exception) {
            log.error("[ZXBroker] 请求数据异常: {}", exception.getMessage());
            return new JSONObject();
        } catch (Exception exception) {
            log.error("[ZXBroker] 请求异常: {}", exception.getMessage());
            return new JSONObject();
        }
    }

    /**
     * 获取券商响应中的 GRID0 数组。
     *
     * @param formParam 表单参数
     * @return GRID0 数组
     */
    public JSONArray requestArray(Map<String, Object> formParam) {
        return request(REQUEST_URL, formParam).getJSONArray("GRID0");
    }

    /**
     * 获取券商响应中的 BINDATA.results 数组。
     *
     * @param formParam 表单参数
     * @return results 数组
     */
    public JSONArray requestBindata(Map<String, Object> formParam) {
        JSONObject data = request(REQUEST_URL + "?action=1230", formParam).getJSONObject("BINDATA");
        return data != null && data.getJSONArray("results") != null ? data.getJSONArray("results") : new JSONArray();
    }

    /**
     * 构建带当前 Token 的请求参数。
     *
     * @param paramMap 业务参数
     * @return 带 Token 的参数
     */
    public Map<String, Object> buildParamsWithToken(Map<String, Object> paramMap) {
        Map<String, Object> params = buildParams(paramMap);
        params.put("token", requireToken());
        params.put("reqno", System.currentTimeMillis());
        return params;
    }

    /**
     * 获取算术验证码并登录券商协议接口。
     * 任何识别不一致都会丢弃当前 CheckToken 并申请新的验证码。
     *
     * @param account 资金账号
     * @param encodedPassword 网页协议使用的加密密码
     * @return 登录成功后的 Token，失败返回 null
     */
    private String loginWithCaptcha(String account, String encodedPassword) {
        int maxRetries = Math.max(1, Math.min(brokerConfig.getCaptchaMaxRetries(), 3));
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                CaptchaChallenge challenge = requestArithmeticCaptcha();
                int checkCode = captchaSolver.solve(challenge.imageBytes());
                JSONObject response = sendLoginRequest(account, encodedPassword, checkCode, challenge.checkToken());
                String loginToken = response.getString("TOKEN");
                if (loginToken != null && !loginToken.isBlank()) {
                    setToken(loginToken);
                    log.info("[ZXBroker] 券商协议登录成功");
                    return loginToken;
                }
                log.warn("[ZXBroker] 第 {} 次登录失败，错误码={}", attempt, response.getString("ERRORNO"));
            } catch (Exception exception) {
                log.warn("[ZXBroker] 第 {} 次验证码处理失败: {}", attempt, exception.getMessage());
            }
        }
        log.error("[ZXBroker] 登录失败，已达到验证码最大重试次数={}", maxRetries);
        return null;
    }

    /**
     * 使用运行环境中的资金账号和加密密码登录。
     *
     * @return 登录成功后的 Token，失败返回 null
     */
    public String loginConfiguredAccount() {
        if (brokerConfig.getAccount().isBlank() || brokerConfig.getEncodedPassword().isBlank()) {
            throw new IllegalStateException("未配置券商资金账号或加密密码");
        }
        return loginWithCaptcha(brokerConfig.getAccount(), brokerConfig.getEncodedPassword());
    }

    private CaptchaChallenge requestArithmeticCaptcha() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", 41092);
        JSONObject response = request(buildParams(params));
        String dataUri = response.getString("MESSAGE");
        String checkToken = response.getString("CHECKTOKEN");
        if (dataUri == null || dataUri.isBlank() || checkToken == null || checkToken.isBlank()) {
            throw new IllegalStateException("验证码接口未返回图片或 CheckToken");
        }
        String base64 = dataUri.substring(dataUri.indexOf(',') + 1);
        try {
            return new CaptchaChallenge(Base64.getDecoder().decode(base64), checkToken);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("验证码图片无法解码", exception);
        }
    }

    private JSONObject sendLoginRequest(String account, String encodedPassword, int checkCode, String checkToken) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", 100);
        params.put("modulus_id", 2);
        params.put("MobileType", 3);
        params.put("accounttype", "ZJACCOUNT");
        params.put("account", account);
        params.put("password", encodedPassword);
        params.put("signkey", brokerConfig.getSignKey());
        params.put("CheckCode", checkCode);
        params.put("CheckToken", checkToken);
        params.put("code", "");
        params.put("maxcount", 100);
        params.put("CHANNEL", "");
        return request(buildParams(params));
    }

    private record CaptchaChallenge(byte[] imageBytes, String checkToken) {
    }
}
// AI_GENERATE_END ---------
