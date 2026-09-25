package com.interchange.platform.common;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 凭据加解密工具。
 * <ul>
 *   <li>登录口令：BCrypt 单向散列；</li>
 *   <li>第三方接口密钥（Basic 密码 / Token）：AES-GCM 加密后入库，页面上不显示明文。</li>
 * </ul>
 * 生产部署建议通过环境变量 APP_SECRET 覆盖默认密钥。
 */
public final class Crypto {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private static final String SECRET = System.getenv("APP_SECRET") == null
            ? "interchange-platform-default-secret-2026"
            : System.getenv("APP_SECRET");

    private static final String PREFIX = "ENC:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Crypto() {
    }

    public static String hashPassword(String raw) {
        return ENCODER.encode(raw);
    }

    public static boolean matchPassword(String raw, String hashed) {
        if (raw == null || hashed == null) {
            return false;
        }
        return ENCODER.matches(raw, hashed);
    }

    /**
     * 业务系统（t6）的口令散列：{@code MD5(salt + 明文)}，32 位小写 hex。
     *
     * <p>与 bytter 平台的 shiro 配置一致（spring-shiro.xml：
     * {@code algorithmName=md5}、{@code hashIterations=1}），
     * 等价于 Shiro {@code new SimpleHash("md5", 明文, salt, 1).toHex()} ——
     * SimpleHash 是先 update(salt) 再 digest(明文)，所以是 salt 在前。
     *
     * <p>平台侧只做校验，不写业务库口令，业务系统怎么改都不受影响。
     */
    public static String hashBizPassword(String raw, String salt) {
        if (raw == null || salt == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            return hex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException("口令散列失败: MD5 不可用");
        }
    }

    public static boolean matchBizPassword(String raw, String salt, String hashed) {
        if (raw == null || salt == null || hashed == null) {
            return false;
        }
        String computed = hashBizPassword(raw, salt);
        return computed != null && computed.equalsIgnoreCase(hashed.trim());
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String s = Integer.toHexString(b & 0xff);
            if (s.length() < 2) {
                sb.append('0');
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /** AES-GCM 加密，输出 ENC:base64(iv+cipher) */
    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        if (plain.startsWith(PREFIX)) {
            return plain;
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new BizException("凭据加密失败: " + e.getMessage());
        }
    }

    /** 解密；非加密内容原样返回，兼容历史明文数据 */
    public static String decrypt(String cipherText) {
        if (cipherText == null || !cipherText.startsWith(PREFIX)) {
            return cipherText;
        }
        try {
            byte[] all = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            byte[] iv = new byte[12];
            System.arraycopy(all, 0, iv, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(all, 12, all.length - 12);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException("凭据解密失败，请检查 APP_SECRET 是否被修改");
        }
    }

    private static SecretKeySpec key() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(SECRET.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }
}
