package com.stock.tradingExecutor.execution;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMultipart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 邮箱短信验证码读取服务。
 * 负责从用户授权的邮箱中读取短信转发邮件，提取 6 位验证码并写入自动登录临时文件。
 *
 * @author mwangli
 * @since 2026-06-30
 */
@Slf4j
@Service
public class EmailSmsCodeService {

    private static final Pattern SMS_CODE_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    /**
     * 是否启用邮箱验证码自动读取。
     */
    @Value("${spring.auto-login.sms-mail.enabled:false}")
    private boolean enabled;

    /**
     * IMAP 主机地址，如 imap.qq.com。
     */
    @Value("${spring.auto-login.sms-mail.host:}")
    private String host;

    /**
     * IMAP 端口，SSL 通常为 993。
     */
    @Value("${spring.auto-login.sms-mail.port:993}")
    private int port;

    /**
     * 邮箱用户名。
     */
    @Value("${spring.auto-login.sms-mail.username:}")
    private String username;

    /**
     * 邮箱授权码或密码。
     */
    @Value("${spring.auto-login.sms-mail.password:}")
    private String password;

    /**
     * 邮件文件夹名称。
     */
    @Value("${spring.auto-login.sms-mail.folder:INBOX}")
    private String folderName;

    /**
     * 发件人关键字过滤，留空则不过滤。
     */
    @Value("${spring.auto-login.sms-mail.sender-keyword:}")
    private String senderKeyword;

    /**
     * 主题或正文关键字过滤，建议配置为券商或短信转发应用名称。
     */
    @Value("${spring.auto-login.sms-mail.content-keyword:中信}")
    private String contentKeyword;

    /**
     * 向前扫描的最近邮件数量，避免全量遍历邮箱。
     */
    @Value("${spring.auto-login.sms-mail.scan-limit:20}")
    private int scanLimit;

    /**
     * 读取到验证码后是否标记邮件已读。
     */
    @Value("${spring.auto-login.sms-mail.mark-seen:false}")
    private boolean markSeen;

    // AI_GENERATED_START
    /**
     * 在指定时间内轮询邮箱验证码，成功后写入 sms_code.txt。
     *
     * @param targetFile 验证码写入文件
     * @param timeout    最大等待时间
     * @return 读取到的 6 位验证码；未启用或超时返回 null
     */
    public String fetchLatestCodeToFile(Path targetFile, Duration timeout) {
        if (!isConfigured()) {
            log.info("[EmailSmsCodeService] 邮箱验证码未启用或配置不完整，继续走人工短信码兜底");
            return null;
        }
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() <= deadline) {
            String code = fetchLatestCodeOnce();
            if (code != null) {
                writeCode(targetFile, code);
                return code;
            }
            sleepQuietly(5_000);
        }
        log.warn("[EmailSmsCodeService] 邮箱验证码等待超时，未读取到有效 6 位验证码");
        return null;
    }

    private boolean isConfigured() {
        return enabled
                && host != null && !host.isBlank()
                && username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }

    private String fetchLatestCodeOnce() {
        Store store = null;
        Folder folder = null;
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", host);
            props.put("mail.imaps.port", String.valueOf(port));
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imaps.connectiontimeout", "10000");
            props.put("mail.imaps.timeout", "10000");

            Session session = Session.getInstance(props);
            store = session.getStore("imaps");
            store.connect(host, port, username, password);

            folder = store.getFolder(folderName);
            folder.open(markSeen ? Folder.READ_WRITE : Folder.READ_ONLY);
            int total = folder.getMessageCount();
            int start = Math.max(1, total - Math.max(scanLimit, 1) + 1);
            Message[] messages = folder.getMessages(start, total);
            for (int i = messages.length - 1; i >= 0; i--) {
                Message message = messages[i];
                String searchableText = buildSearchableText(message);
                if (!matchesFilter(searchableText)) {
                    continue;
                }
                Matcher matcher = SMS_CODE_PATTERN.matcher(searchableText);
                if (matcher.find()) {
                    if (markSeen) {
                        message.setFlag(Flags.Flag.SEEN, true);
                    }
                    log.info("[EmailSmsCodeService] 已从邮箱读取到短信验证码");
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            log.warn("[EmailSmsCodeService] 读取邮箱验证码失败: {}", e.getMessage());
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
        return null;
    }

    private String buildSearchableText(Message message) throws Exception {
        StringBuilder builder = new StringBuilder();
        if (message.getSubject() != null) {
            builder.append(message.getSubject()).append('\n');
        }
        if (message.getFrom() != null) {
            for (var address : message.getFrom()) {
                builder.append(address).append('\n');
            }
        }
        Object content = message.getContent();
        builder.append(extractText(content));
        return builder.toString();
    }

    private String extractText(Object content) throws Exception {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof MimeMultipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                builder.append(extractText(multipart.getBodyPart(i).getContent())).append('\n');
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }

    private boolean matchesFilter(String text) {
        boolean senderOk = senderKeyword == null || senderKeyword.isBlank() || text.contains(senderKeyword);
        boolean contentOk = contentKeyword == null || contentKeyword.isBlank() || text.contains(contentKeyword);
        return senderOk && contentOk;
    }

    private void writeCode(Path targetFile, String code) {
        try {
            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, code, StandardCharsets.UTF_8);
            log.info("[EmailSmsCodeService] 验证码已写入文件: {}", targetFile);
        } catch (Exception e) {
            log.warn("[EmailSmsCodeService] 写入验证码文件失败: {}", e.getMessage());
        }
    }

    private void closeQuietly(Folder folder) {
        try {
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
        } catch (Exception ignored) {
        }
    }

    private void closeQuietly(Store store) {
        try {
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    // AI_GENERATED_END
}
