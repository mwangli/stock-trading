// AI_GENERATE_START ---
package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.execution.ZXBrokerConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 券商和账户内部标识服务。
 * 资金账号及股东账号仅用于计算稳定哈希，不以明文写入业务数据库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class BrokerIdentityService {

    private final ZXBrokerConfig zxBrokerConfig;

    /**
     * 获取标准券商编码。
     *
     * @return CITIC
     */
    public String getBrokerCode() {
        return "CITIC";
    }

    /**
     * 获取稳定的账户哈希标识。
     *
     * @return 不含账户明文的内部标识
     */
    public String getAccountId() {
        String account = zxBrokerConfig.getAccount();
        if (account == null || account.isBlank()) {
            throw new IllegalStateException("中信券商账户未配置，无法生成稳定账户标识");
        }
        return "acct_" + sha256(account.trim()).substring(0, 24);
    }

    /**
     * 对敏感协议字段生成稳定哈希。
     *
     * @param value 敏感字段明文
     * @return SHA-256 十六进制字符串；空值返回空字符串
     */
    public String hashSensitiveValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return sha256(value.trim());
    }

    /**
     * 对业务幂等材料生成稳定 SHA-256 键。
     *
     * @param value 业务幂等材料
     * @return 64 位十六进制哈希
     */
    public String stableKey(String value) {
        return sha256(value != null ? value : "");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", exception);
        }
    }
}
// AI_GENERATE_END ---
