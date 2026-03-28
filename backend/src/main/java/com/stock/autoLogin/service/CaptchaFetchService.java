package com.stock.autoLogin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 验证码获取服务
 * 通过邮件 IMAP 协议获取中信证券验证码
 *
 * @author mwangli
 * @since 2026-03-27
 */
@Slf4j
@Service
public class CaptchaFetchService {

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

    public CaptchaFetchService() {
        log.info("验证码获取服务初始化：邮件直连模式");
    }

    /**
     * 初始化邮箱配置
     */
    public void init() {
        emailConfigured = !emailUsername.isEmpty() && !emailPassword.isEmpty();
        log.info("邮箱配置状态: host={}, port={}, username={}, configured={}",
                emailHost, emailPort, emailUsername, emailConfigured);
    }

    /**
     * 获取手机验证码（直接通过邮件获取）
     *
     * @return 6位数字验证码，或 null
     */
    public String getPhoneCode() {
        log.info("========== 开始获取邮件验证码 ==========");

        if (!emailConfigured) {
            log.warn("邮箱未配置，无法获取验证码");
            return null;
        }

        return fetchLatestEmail();
    }

    /**
     * 获取手机验证码（带失效重试）
     * 如果验证失败需要重新获取，调用此方法
     *
     * @param resendCodeFunc 重新发送验证码的函数引用
     * @return 6位数字验证码，或 null
     */
    public String getPhoneCodeWithRetry(java.util.function.Supplier<Boolean> resendCodeFunc) {
        log.info("========== 开始获取邮件验证码（带失效重试）==========");

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
     * 从邮件获取验证码
     */
    private String fetchLatestEmail() {
        try {
            log.info("从邮件获取验证码...");
            String emailContent = fetchFromEmailServer();
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
     * 连接邮件服务器获取最新邮件内容
     */
    private String fetchFromEmailServer() throws Exception {
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
                log.info("从邮件中提取到验证码: {}****", code.substring(0, 2));
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
