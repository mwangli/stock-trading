package com.stock.autoLogin.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 验证码获取服务
 * 支持从 Redis 和邮件两种方式获取短信验证码
 * 增强版：3次Redis重试 + 退阶邮件获取 + 失效自动重发
 *
 * @author mwangli
 * @since 2026-03-27
 */
@Slf4j
@Service
public class CaptchaFetchService {

    private static final String PHONE_CODE_KEY = "PHONE_CODE";
    private static final Pattern SMS_CODE_PATTERN = Pattern.compile("(\\d{6})");

    @Value("${spring.email.host:imap.qq.com}")
    private String emailHost;

    @Value("${spring.email.port:993}")
    private int emailPort;

    @Value("${spring.email.username:}")
    private String emailUsername;

    @Value("${spring.email.password:}")
    private String emailPassword;

    private boolean emailConfigured = false;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @PostConstruct
    public void init() {
        emailConfigured = !emailUsername.isEmpty() && !emailPassword.isEmpty();
        log.info("验证码获取服务初始化: Redis可用={}, 邮箱已配置={}", redisTemplate != null, emailConfigured);
    }

    /**
     * 获取手机验证码（增强版）
     * 先从 Redis 获取，失败则重试 3 次（等待 10s, 20s, 30s）
     * Redis 3 次都失败，则从邮件获取
     *
     * @return 6位数字验证码，或 null
     */
    public String getPhoneCode() {
        log.info("========== 开始获取手机验证码（增强版）==========");

        String code = tryGetFromRedisWithRetry();
        if (code != null) {
            return code;
        }

        log.warn("Redis 获取验证码失败，尝试从邮件获取...");
        return getCodeFromEmail();
    }

    /**
     * 获取手机验证码（带失效重试）
     * 如果验证失败需要重新获取，调用此方法
     *
     * @param resendCodeFunc 重新发送验证码的函数引用
     * @return 6位数字验证码，或 null
     */
    public String getPhoneCodeWithRetry(java.util.function.Supplier<Boolean> resendCodeFunc) {
        log.info("========== 开始获取手机验证码（带失效重试）==========");

        for (int attempt = 1; attempt <= 3; attempt++) {
            log.info("第 {}/3 次尝试获取验证码", attempt);

            String code = getPhoneCode();
            if (code != null) {
                return code;
            }

            if (attempt < 3) {
                log.warn("第 {} 次获取失败，准备重新发送验证码...", attempt);

                if (resendCodeFunc != null && resendCodeFunc.get()) {
                    log.info("验证码已重新发送，继续等待...");
                } else {
                    log.warn("重新发送失败或未提供发送函数");
                }
            }
        }

        log.error("3次获取验证码均失败");
        return null;
    }

    /**
     * 从 Redis 获取验证码，支持重试
     * 重试策略：10s -> 20s -> 30s
     */
    private String tryGetFromRedisWithRetry() {
        if (redisTemplate == null) {
            log.warn("RedisTemplate 未配置，跳过 Redis 获取");
            return null;
        }

        int[] retryDelays = {10, 20, 30};

        for (int i = 0; i < retryDelays.length; i++) {
            int delay = retryDelays[i];
            log.info("尝试 {}/{}: 等待 {} 秒后从 Redis 获取验证码", i + 1, retryDelays.length, delay);

            if (i > 0) {
                try {
                    Thread.sleep(delay * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("等待被中断");
                    break;
                }
            }

            String code = fetchFromRedis();
            if (code != null) {
                log.info("Redis 获取验证码成功: {}", code);
                return code;
            }

            log.warn("第 {}/{} 次 Redis 获取失败", i + 1, retryDelays.length);
        }

        return null;
    }

    /**
     * 从 Redis 获取验证码（一次性，获取后删除）
     */
    public String fetchFromRedis() {
        if (redisTemplate == null) {
            return null;
        }

        try {
            Object code = redisTemplate.opsForValue().get(PHONE_CODE_KEY);
            if (code != null) {
                String codeStr = code.toString();
                if (isValidSmsCode(codeStr)) {
                    redisTemplate.delete(PHONE_CODE_KEY);
                    log.info("Redis 验证码: {}", codeStr);
                    return codeStr;
                }
            }
            log.debug("Redis 中未找到有效验证码");
            return null;
        } catch (Exception e) {
            log.error("从 Redis 获取验证码异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 查询 Redis 中的验证码（不删除）
     */
    public String peekRedisCode() {
        if (redisTemplate == null) {
            return null;
        }

        try {
            Object code = redisTemplate.opsForValue().get(PHONE_CODE_KEY);
            if (code != null) {
                return code.toString();
            }
            return null;
        } catch (Exception e) {
            log.error("查询 Redis 验证码失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 清除 Redis 中的验证码
     */
    public boolean clearRedisCode() {
        if (redisTemplate == null) {
            return false;
        }

        try {
            Boolean deleted = redisTemplate.delete(PHONE_CODE_KEY);
            log.info("清除 Redis 验证码: {}", deleted);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.error("清除 Redis 验证码失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从邮件获取验证码
     */
    private String getCodeFromEmail() {
        if (!emailConfigured) {
            log.warn("邮箱未配置，无法从邮件获取验证码");
            return null;
        }

        try {
            log.info("从邮件获取验证码...");
            String emailContent = fetchLatestEmail();
            if (emailContent == null) {
                log.warn("未找到包含验证码的邮件");
                return null;
            }

            return extractCodeFromEmail(emailContent);
        } catch (Exception e) {
            log.error("从邮件获取验证码失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取最新邮件内容
     */
    private String fetchLatestEmail() throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", emailHost);
        props.put("mail.imaps.port", emailPort);
        props.put("mail.imaps.ssl.enable", "true");

        jakarta.mail.Session session = jakarta.mail.Session.getInstance(props);
        jakarta.mail.Store store = session.getStore("imaps");
        store.connect(emailHost, emailPort, emailUsername, emailPassword);

        jakarta.mail.Folder inbox = store.getFolder("INBOX");
        inbox.open(jakarta.mail.Folder.READ_ONLY);

        jakarta.mail.Message[] messages = inbox.getMessages();
        if (messages.length == 0) {
            inbox.close(false);
            store.close();
            return null;
        }

        jakarta.mail.Message latestMessage = messages[messages.length - 1];
        String subject = latestMessage.getSubject();

        log.info("最新邮件主题: {}", subject);

        if (subject != null && (subject.contains("验证码") || subject.contains("登录") || subject.contains("中信"))) {
            String content = getEmailContent(latestMessage);
            inbox.close(false);
            store.close();
            return content;
        }

        for (int i = messages.length - 1; i >= Math.max(0, messages.length - 10); i--) {
            String msgSubject = messages[i].getSubject();
            if (msgSubject != null && (msgSubject.contains("验证码") || msgSubject.contains("登录"))) {
                String content = getEmailContent(messages[i]);
                inbox.close(false);
                store.close();
                return content;
            }
        }

        inbox.close(false);
        store.close();
        return null;
    }

    /**
     * 获取邮件正文内容
     */
    private String getEmailContent(jakarta.mail.Message message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String) {
            return (String) content;
        } else if (content instanceof jakarta.mail.Multipart) {
            jakarta.mail.Multipart multipart = (jakarta.mail.Multipart) content;
            StringBuilder textContent = new StringBuilder();

            for (int i = 0; i < multipart.getCount(); i++) {
                jakarta.mail.BodyPart bodyPart = multipart.getBodyPart(i);
                if (bodyPart.isMimeType("text/plain")) {
                    textContent.append(bodyPart.getContent().toString());
                } else if (bodyPart.isMimeType("text/html")) {
                    textContent.append(bodyPart.getContent().toString());
                }
            }

            return textContent.toString();
        }
        return "";
    }

    /**
     * 从邮件内容中提取6位数字验证码
     */
    private String extractCodeFromEmail(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        Matcher matcher = SMS_CODE_PATTERN.matcher(content);
        while (matcher.find()) {
            String code = matcher.group(1);
            if (isValidSmsCode(code)) {
                log.info("从邮件中提取到验证码: {}", code);
                return code;
            }
        }

        log.warn("邮件内容中未找到6位数字验证码");
        return null;
    }

    /**
     * 验证是否为有效的短信验证码
     */
    private boolean isValidSmsCode(String code) {
        if (code == null || code.length() != 6) {
            return false;
        }
        return code.matches("\\d{6}");
    }
}
