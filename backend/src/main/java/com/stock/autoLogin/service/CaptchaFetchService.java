package com.stock.autoLogin.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 验证码获取服务
 * 优先从 Redis 获取验证码，回退到邮件 IMAP 协议获取中信证券验证码
 * 支持递增间隔重试（10s → 30s → 60s）
 *
 * @author mwangli
 * @since 2026-03-27
 */
@Slf4j
@Service
public class CaptchaFetchService {

    private static final Pattern SMS_CODE_PATTERN = Pattern.compile("(\\d{6})");

    /** 重试间隔（毫秒）：10s → 30s → 60s */
    private static final long[] RETRY_DELAYS = {10_000L, 30_000L, 60_000L};

    /** 验证码有效时间窗口（5分钟内的邮件） */
    private static final long CODE_VALID_WINDOW_MS = 5 * 60 * 1000L;

    /** Redis 中验证码的 key 前缀 */
    private static final String REDIS_CODE_KEY_PREFIX = "sms:code:";

    @Value("${spring.email.host:imap.qq.com}")
    private String emailHost;

    @Value("${spring.email.port:993}")
    private int emailPort;

    @Value("${spring.email.username:}")
    private String emailUsername;

    @Value("${spring.email.password:}")
    private String emailPassword;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private boolean emailConfigured = false;
    private boolean redisAvailable = false;

    /**
     * 默认构造函数
     */
    public CaptchaFetchService() {
        log.info("验证码获取服务初始化：Redis优先 + 邮件直连模式");
    }

    /**
     * 初始化邮箱配置和 Redis 连接状态
     */
    @PostConstruct
    public void init() {
        // 1. 检查邮箱配置
        emailConfigured = !emailUsername.isEmpty() && !emailPassword.isEmpty();
        log.info("邮箱配置状态: host={}, port={}, username={}, configured={}",
                emailHost, emailPort, emailUsername, emailConfigured);

        // 2. 检查 Redis 连接
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().get("ping");
                redisAvailable = true;
                log.info("Redis 连接正常，验证码将优先从 Redis 获取");
            } catch (Exception e) {
                redisAvailable = false;
                log.warn("Redis 不可用，将仅使用邮件获取验证码: {}", e.getMessage());
            }
        } else {
            log.info("RedisTemplate 未注入，跳过 Redis 验证码获取");
        }
    }

    /**
     * 获取手机验证码（优先 Redis，回退邮件）
     *
     * @return 6位数字验证码，或 null
     */
    public String getPhoneCode() {
        log.info("========== 开始获取验证码 ==========");

        // 1. 优先从 Redis 获取
        String code = getCodeFromRedis();
        if (code != null) {
            return code;
        }

        // 2. 回退到邮件获取
        if (!emailConfigured) {
            log.warn("邮箱未配置，无法获取验证码");
            return null;
        }

        return fetchLatestEmail();
    }

    /**
     * 获取手机验证码（带递增间隔重试）
     * 重试间隔：10s → 30s → 60s
     *
     * @param resendCodeFunc 重新发送验证码的函数引用
     * @return 6位数字验证码，或 null
     */
    public String getPhoneCodeWithRetry(java.util.function.Supplier<Boolean> resendCodeFunc) {
        log.info("========== 开始获取验证码（递增间隔重试）==========");

        for (int attempt = 0; attempt < RETRY_DELAYS.length; attempt++) {
            log.info("第 {}/{} 次尝试获取验证码", attempt + 1, RETRY_DELAYS.length);

            String code = getPhoneCode();
            if (code != null) {
                return code;
            }

            if (attempt < RETRY_DELAYS.length - 1) {
                long delay = RETRY_DELAYS[attempt];
                log.warn("第 {} 次获取失败，等待 {}s 后重试...", attempt + 1, delay / 1000);

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("等待被中断");
                    return null;
                }

                // 重新发送验证码
                if (resendCodeFunc != null) {
                    try {
                        resendCodeFunc.get();
                        log.info("验证码已重新发送");
                    } catch (Exception e) {
                        log.warn("重新发送验证码失败: {}", e.getMessage());
                    }
                }
            }
        }

        log.error("{}次获取验证码均失败", RETRY_DELAYS.length);
        return null;
    }

    /**
     * 从 Redis 获取验证码
     *
     * @return 验证码或 null
     */
    private String getCodeFromRedis() {
        if (!redisAvailable || redisTemplate == null) {
            return null;
        }
        try {
            // 尝试多种可能的 key 格式
            String[] keys = {
                    REDIS_CODE_KEY_PREFIX + "13278828091",
                    REDIS_CODE_KEY_PREFIX + "latest",
                    "captcha:sms:latest"
            };
            for (String key : keys) {
                Object value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    String code = value.toString();
                    if (code.matches("\\d{6}")) {
                        log.info("从 Redis 获取到验证码 (key={}): {}****", key, code.substring(0, 2));
                        return code;
                    }
                }
            }
            log.info("Redis 中未找到验证码");
        } catch (Exception e) {
            log.warn("Redis 获取验证码异常: {}", e.getMessage());
            redisAvailable = false;
        }
        return null;
    }

    /**
     * 从邮件获取验证码
     */
    private String fetchLatestEmail() {
        try {
            log.info("从邮件获取验证码...");
            String emailContent = fetchFromEmailServer();
            if (emailContent == null) {
                log.warn("未找到包含验证码的近期邮件");
                return null;
            }

            return extractCodeFromEmail(emailContent);
        } catch (Exception e) {
            log.error("从邮件获取验证码失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 连接邮件服务器获取最新邮件内容
     * 优化：仅读取最近 10 封邮件，且过滤 5 分钟内的验证码邮件
     */
    private String fetchFromEmailServer() throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", emailHost);
        props.put("mail.imaps.port", emailPort);
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.timeout", "10000");
        props.put("mail.imaps.connectiontimeout", "10000");

        jakarta.mail.Session session = jakarta.mail.Session.getInstance(props);
        jakarta.mail.Store store = session.getStore("imaps");

        log.info("连接 IMAP 服务器: {}:{}", emailHost, emailPort);
        store.connect(emailHost, emailPort, emailUsername, emailPassword);
        log.info("IMAP 连接成功");

        jakarta.mail.Folder inbox = store.getFolder("INBOX");
        inbox.open(jakarta.mail.Folder.READ_ONLY);

        int messageCount = inbox.getMessageCount();
        log.info("邮箱共有 {} 封邮件，仅检查最近 10 封", messageCount);

        if (messageCount == 0) {
            inbox.close(false);
            store.close();
            return null;
        }

        // 只读取最近 10 封邮件（避免全量加载几百封）
        int startIndex = Math.max(1, messageCount - 9);
        jakarta.mail.Message[] messages = inbox.getMessages(startIndex, messageCount);

        // 时间窗口：只接受 5 分钟内的邮件
        Date cutoffTime = new Date(System.currentTimeMillis() - CODE_VALID_WINDOW_MS);

        // 从最新往前遍历
        for (int i = messages.length - 1; i >= 0; i--) {
            jakarta.mail.Message msg = messages[i];
            String subject = msg.getSubject();
            Date sentDate = msg.getSentDate();

            // 跳过超过时效的邮件
            if (sentDate != null && sentDate.before(cutoffTime)) {
                log.info("邮件 [{}] 发送时间 {} 超过5分钟，停止搜索", subject, sentDate);
                break;
            }

            log.info("检查邮件: subject={}, sentDate={}", subject, sentDate);

            if (subject != null && (subject.contains("验证码") || subject.contains("登录") || subject.contains("中信"))) {
                String content = getEmailContent(msg);
                log.info("找到验证码邮件: {}", subject);
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
     *
     * @param message 邮件消息
     * @return 邮件正文文本
     * @throws Exception 读取异常
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
     *
     * @param content 邮件正文
     * @return 6位验证码或 null
     */
    private String extractCodeFromEmail(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        Matcher matcher = SMS_CODE_PATTERN.matcher(content);
        while (matcher.find()) {
            String code = matcher.group(1);
            if (isValidSmsCode(code)) {
                log.info("从邮件中提取到验证码: {}****", code.substring(0, 2));
                return code;
            }
        }

        log.warn("邮件内容中未找到6位数字验证码");
        return null;
    }

    /**
     * 验证是否为有效的短信验证码
     *
     * @param code 待验证的字符串
     * @return 是否为有效6位数字
     */
    private boolean isValidSmsCode(String code) {
        if (code == null || code.length() != 6) {
            return false;
        }
        return code.matches("\\d{6}");
    }

    /**
     * 测试邮件连接和读取（诊断用）
     *
     * @return 测试结果描述
     */
    public String testEmailConnection() {
        if (!emailConfigured) {
            return "邮箱未配置: username=" + emailUsername;
        }

        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", emailHost);
            props.put("mail.imaps.port", emailPort);
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imaps.timeout", "10000");
            props.put("mail.imaps.connectiontimeout", "10000");

            jakarta.mail.Session session = jakarta.mail.Session.getInstance(props);
            jakarta.mail.Store store = session.getStore("imaps");
            store.connect(emailHost, emailPort, emailUsername, emailPassword);

            jakarta.mail.Folder inbox = store.getFolder("INBOX");
            inbox.open(jakarta.mail.Folder.READ_ONLY);

            int total = inbox.getMessageCount();
            int unread = inbox.getUnreadMessageCount();

            // 读取最新一封邮件标题
            String latestSubject = "无邮件";
            if (total > 0) {
                jakarta.mail.Message latest = inbox.getMessage(total);
                latestSubject = latest.getSubject() + " (" + latest.getSentDate() + ")";
            }

            inbox.close(false);
            store.close();

            return String.format("连接成功! 总邮件=%d, 未读=%d, 最新邮件=%s", total, unread, latestSubject);
        } catch (Exception e) {
            return "连接失败: " + e.getMessage();
        }
    }
}
