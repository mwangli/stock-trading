package com.stock.tradingExecutor.execution;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 中信证券API请求工具类
 * 封装HTTP请求和Token管理
 *
 * @author mwangli
 * @since 2026-03-21
 */
@Slf4j
@Component
public class ZXRequestUtils {

    private static final String REQUEST_URL = "https://weixin.citicsinfo.com/reqxml";
    private static final int RETRY_TIMES = 10;
    private static final int MAX_CAPTCHA_RETRY = 3;
    private static final String REDIS_TOKEN_KEY = "citics:token";
    private static final long TOKEN_EXPIRE_MINUTES = 30;

    private final com.stock.autoLogin.service.CaptchaService captchaService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 使用本地内存缓存请求 Token，作为Redis的备份
     */
    private volatile String token;
    private volatile long tokenExpireAtMs;

    public ZXRequestUtils(
            com.stock.autoLogin.service.CaptchaService captchaService,
            @Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.captchaService = captchaService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 构建通用请求参数
     */
    public Map<String, Object> buildParams(Map<String, Object> paramMap) {
        if (paramMap == null) {
            paramMap = new HashMap<>();
        }
        paramMap.put("cfrom", "H5");
        paramMap.put("tfrom", "PC");
        paramMap.put("newindex", "1");
        paramMap.put("MobileCode", "13278828091");
        paramMap.put("intacttoserver", "@ClZvbHVtZUluZm8JAAAANTI1QS00Qjc4");
        paramMap.put("reqno", System.currentTimeMillis());
        return paramMap;
    }

    /**
     * 获取Token
     * 优先从Redis获取，Redis不可用时使用本地内存缓存
     */
    public String getToken() {
        long now = System.currentTimeMillis();

        if (redisTemplate != null) {
            try {
                Object cachedToken = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
                if (cachedToken != null) {
                    String tokenStr = cachedToken.toString();
                    if (!tokenStr.isEmpty()) {
                        log.debug("[ZXRequestUtils] 从Redis获取Token成功");
                        return tokenStr;
                    }
                }
            } catch (Exception e) {
                log.warn("[ZXRequestUtils] 从Redis获取Token失败，使用内存缓存: {}", e.getMessage());
            }
        }

        if (token != null && now < tokenExpireAtMs) {
            return token;
        }
        return null;
    }

    /**
     * 设置Token
     * 同时写入Redis和本地内存缓存
     */
    public void setToken(String token) {
        if (token != null) {
            this.token = token;
            this.tokenExpireAtMs = System.currentTimeMillis() + TOKEN_EXPIRE_MINUTES * 60L * 1000L;

            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(REDIS_TOKEN_KEY, token, TOKEN_EXPIRE_MINUTES, TimeUnit.MINUTES);
                    log.info("[ZXRequestUtils] Token已写入Redis，Key: {}, 过期时间: {}分钟", REDIS_TOKEN_KEY, TOKEN_EXPIRE_MINUTES);
                } catch (Exception e) {
                    log.warn("[ZXRequestUtils] Token写入Redis失败，仅保存在内存: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 清除Token
     */
    public void clearToken() {
        this.token = null;
        this.tokenExpireAtMs = 0;

        if (redisTemplate != null) {
            try {
                redisTemplate.delete(REDIS_TOKEN_KEY);
                log.info("[ZXRequestUtils] Token已从Redis清除");
            } catch (Exception e) {
                log.warn("[ZXRequestUtils] Token从Redis清除失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 发送HTTP请求，返回JSONObject
     */
    public JSONObject request(Map<String, Object> formParam) {
        return request(REQUEST_URL, formParam, 0);
    }

    /**
     * 发送HTTP请求，返回JSONObject
     */
    public JSONObject request(String url, Map<String, Object> formParam, int times) {
        try {
            if (times > RETRY_TIMES) {
                log.error("[ZXBroker] 请求错误次数过多,请检查程序代码!");
                return new JSONObject();
            }

            String response = HttpUtil.createPost(url).form(formParam).execute().body();

            if (log.isDebugEnabled()) {
                log.debug("[ZXBroker] 响应: {}",
                    response.length() > 1000 ? response.substring(0, 1000) : response);
            }

            JSONObject res = JSONObject.parseObject(response);
            String newToken = res.getString("TOKEN");
            if (newToken != null) {
                setToken(newToken);
            }
            return res;
        } catch (JSONException e) {
            log.error("[ZXBroker] 请求数据异常: {}", e.getMessage());
            return new JSONObject();
        } catch (Exception e) {
            log.error("[ZXBroker] 请求异常: {}", e.getMessage());
            return new JSONObject();
        }
    }

    /**
     * 发送HTTP请求，返回JSONArray (GRID0字段)
     */
    public JSONArray requestArray(Map<String, Object> formParam) {
        JSONObject res = request(REQUEST_URL, formParam, 0);
        return res.getJSONArray("GRID0");
    }

    /**
     * 发送HTTP请求，返回JSONArray (BINDATA.results字段)
     */
    public JSONArray requestBindata(Map<String, Object> formParam) {
        JSONObject res = request(REQUEST_URL + "?action=1230", formParam, 0);
        JSONObject data = res.getJSONObject("BINDATA");
        if (data != null && data.getJSONArray("results") != null) {
            return data.getJSONArray("results");
        }
        return new JSONArray();
    }

    /**
     * 构建带Token的参数
     */
    public Map<String, Object> buildParamsWithToken(Map<String, Object> paramMap) {
        Map<String, Object> params = buildParams(paramMap);
        params.put("token", getToken());
        params.put("reqno", System.currentTimeMillis());
        return params;
    }

    /**
     * 带滑块验证码的登录
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录成功后的Token，失败返回null
     */
    public String loginWithCaptcha(String username, String password) {
        for (int retry = 0; retry < MAX_CAPTCHA_RETRY; retry++) {
            JSONObject loginResponse = sendLoginRequest(username, password);

            if (loginResponse == null) {
                log.error("[ZXRequestUtils] 登录请求失败");
                continue;
            }

            log.info("[ZXRequestUtils] 登录响应: {}", loginResponse.toJSONString());

            if (!loginResponse.containsKey("need_captcha") || !loginResponse.getBoolean("need_captcha")) {
                String token = loginResponse.getString("token");
                if (token != null) {
                    log.info("[ZXRequestUtils] 登录成功，无需验证码");
                    setToken(token);
                    return token;
                }
            }

            String captchaToken = loginResponse.getString("token");
            String bgImageUrl = loginResponse.getString("bg_image_url");
            String sliderImageUrl = loginResponse.getString("slider_image_url");

            if (bgImageUrl == null || sliderImageUrl == null) {
                log.error("[ZXRequestUtils] 验证码图片URL为空，captchaToken: {}", captchaToken);
                continue;
            }

            byte[] bgImage = getCaptchaImage(bgImageUrl);
            byte[] sliderImage = getCaptchaImage(sliderImageUrl);

            if (bgImage.length == 0 || sliderImage.length == 0) {
                log.error("[ZXRequestUtils] 获取验证码图片失败");
                continue;
            }

            int distance = captchaService.calculateDistance(bgImage, sliderImage);
            log.info("[ZXRequestUtils] 计算滑块距离: {}px", distance);

            List<Integer> track = captchaService.generateSlideTrack(distance);

            boolean verifySuccess = submitCaptchaResult(captchaToken, distance, track);
            if (verifySuccess) {
                JSONObject finalResponse = sendLoginRequest(username, password);
                String token = finalResponse != null ? finalResponse.getString("token") : null;
                if (token != null) {
                    log.info("[ZXRequestUtils] 验证码验证后登录成功");
                    setToken(token);
                    return token;
                }
            }

            log.warn("[ZXRequestUtils] 第{}次验证码验证失败", retry + 1);
        }

        log.error("[ZXRequestUtils] 验证码验证失败次数过多");
        return null;
    }

    /**
     * 发送登录请求
     */
    private JSONObject sendLoginRequest(String username, String password) {
        Map<String, Object> loginParams = new HashMap<>();
        loginParams.put("username", username);
        loginParams.put("password", password);
        loginParams.put("reqno", System.currentTimeMillis());

        try {
            String response = HttpUtil.createPost(REQUEST_URL + "?action=1001")
                    .form(buildParams(loginParams))
                    .execute()
                    .body();
            return JSONObject.parseObject(response);
        } catch (Exception e) {
            log.error("[ZXRequestUtils] 登录请求异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 提交验证码验证结果
     */
    private boolean submitCaptchaResult(String captchaToken, int distance, List<Integer> track) {
        Map<String, Object> captchaParams = new HashMap<>();
        captchaParams.put("captcha_token", captchaToken);
        captchaParams.put("distance", distance);
        captchaParams.put("track", String.join(",", track.stream().map(String::valueOf).toList()));
        captchaParams.put("reqno", System.currentTimeMillis());

        try {
            String response = HttpUtil.createPost(REQUEST_URL + "?action=1002")
                    .form(buildParams(captchaParams))
                    .execute()
                    .body();
            JSONObject res = JSONObject.parseObject(response);
            return res != null && "0".equals(res.getString("error_no"));
        } catch (Exception e) {
            log.error("[ZXRequestUtils] 验证码提交异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取验证码图片
     *
     * @param imageUrl 图片URL
     * @return 图片字节数组
     */
    public byte[] getCaptchaImage(String imageUrl) {
        try {
            return HttpUtil.createGet(imageUrl).execute().bodyBytes();
        } catch (Exception e) {
            log.error("[ZXRequestUtils] 获取验证码图片失败: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * 发送HTTP GET请求
     *
     * @param url 请求URL
     * @return 响应字符串
     */
    public static String httpGet(String url) {
        try {
            return HttpUtil.createGet(url).execute().body();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 下载图片
     *
     * @param url 图片URL
     * @return 图片字节数组
     */
    public static byte[] httpGetImage(String url) {
        try {
            return HttpUtil.createGet(url).execute().bodyBytes();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 发送带请求头的HTTP GET请求
     *
     * @param url 请求URL
     * @return 响应字符串
     */
    public static String httpGetWithHeaders(String url) {
        try {
            return HttpUtil.createGet(url)
                    .header("accept", "*")
                    .header("accept-encoding", "gzip, deflate, br, zstd")
                    .header("accept-language", "zh-CN,zh;q=0.9")
                    .header("cache-control", "no-cache")
                    .header("connection", "keep-alive")
                    .header("host", "c.dun.163.com")
                    .header("pragma", "no-cache")
                    .header("referer", "https://weixin.citicsinfo.com/")
                    .header("sec-ch-ua", "\"Chromium\";v=\"146\", \"Not-A.Brand\";v=\"24\", \"Google Chrome\";v=\"146\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"Windows\"")
                    .header("sec-fetch-dest", "script")
                    .header("sec-fetch-mode", "no-cors")
                    .header("sec-fetch-site", "cross-site")
                    .header("sec-fetch-storage-access", "active")
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36")
                    .execute()
                    .body();
        } catch (Exception e) {
            return null;
        }
    }
}
