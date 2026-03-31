package com.stock.autoLogin.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES 加密解密工具
 * <p>
 * 用于前后端敏感数据（如 API 密钥）的加密传输。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-31
 */
@Slf4j
@Component
public class AesEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final int KEY_SIZE = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) {
        AesEncryptor encryptor = new AesEncryptor();
        String secretKey = encryptor.generateSecretKey();
        System.out.println("生成的密钥: " + secretKey);

        String original = "mySecretApiKey123";
        String encrypted = encryptor.encrypt(original, secretKey);
        System.out.println("加密后: " + encrypted);

        String decrypted = encryptor.decrypt(encrypted, secretKey);
        System.out.println("解密后: " + decrypted);

        System.out.println("验证: " + original.equals(decrypted));
    }

    /**
     * 生成随机 AES 密钥
     *
     * @return Base64 编码的 16 字节密钥
     */
    public String generateSecretKey() {
        byte[] keyBytes = new byte[KEY_SIZE];
        RANDOM.nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    /**
     * AES 加密
     *
     * @param plainText 明文
     * @param secretKey Base64 编码的密钥
     * @return Base64 编码的密文
     */
    public String encrypt(String plainText, String secretKey) {
        if (plainText == null || secretKey == null) {
            throw new IllegalArgumentException("明文和密钥不能为空");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(secretKey);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("AES 加密失败", e);
            throw new RuntimeException("AES 加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * AES 解密
     *
     * @param encryptedText Base64 编码的密文
     * @param secretKey     Base64 编码的密钥
     * @return 解密后的明文
     */
    public String decrypt(String encryptedText, String secretKey) {
        if (encryptedText == null || secretKey == null) {
            throw new IllegalArgumentException("密文和密钥不能为空");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(secretKey);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            byte[] encrypted = Base64.getDecoder().decode(encryptedText);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES 解密失败", e);
            throw new RuntimeException("AES 解密失败: " + e.getMessage(), e);
        }
    }
}
