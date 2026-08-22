package io.haoblog.comment.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

@Service
public class CommentSecurityService {
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int EMAIL_KEY_VERSION = 1;
    private final byte[] emailKey;
    private final byte[] ipKey;
    private final byte[] challengeKey;
    private final SecureRandom random = new SecureRandom();

    public CommentSecurityService(@Value("${haoblog.comment.security-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalArgumentException("HAOBLOG_COMMENT_SECURITY_KEY must be configured");
        }
        byte[] masterKey;
        try {
            masterKey = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("HAOBLOG_COMMENT_SECURITY_KEY must be Base64", exception);
        }
        if (masterKey.length != KEY_BYTES) {
            throw new IllegalArgumentException("HAOBLOG_COMMENT_SECURITY_KEY must decode to 32 bytes");
        }
        this.emailKey = derive(masterKey, "email-v1");
        this.ipKey = derive(masterKey, "ip-v1");
        this.challengeKey = derive(masterKey, "challenge-v1");
    }

    public EmailCiphertext encryptEmail(UUID commentId, String email) {
        if (email == null || email.isBlank()) return null;
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(emailKey, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(commentId.toString().getBytes(StandardCharsets.UTF_8));
            return new EmailCiphertext(EMAIL_KEY_VERSION, nonce,
                    cipher.doFinal(email.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt comment email", exception);
        }
    }

    public String decryptEmail(UUID commentId, EmailCiphertext encrypted) {
        if (encrypted == null || encrypted.keyVersion() != EMAIL_KEY_VERSION) {
            throw new IllegalArgumentException("Unsupported comment email key version");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(emailKey, "AES"),
                    new GCMParameterSpec(128, encrypted.nonce()));
            cipher.updateAAD(commentId.toString().getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted.ciphertext()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Comment email authentication failed", exception);
        }
    }

    public byte[] dailyIpHmac(String ip, LocalDate date) {
        return hmac(ipKey, "ip-v1:" + date + ":" + ip);
    }

    public byte[] contentFingerprint(UUID articleId, String normalizedBody) {
        return digest(articleId + "\n" + normalizedBody);
    }

    public byte[] deleteTokenDigest(String token) {
        return digest(token);
    }

    public String challengeToken(UUID articleId, java.time.Instant issuedAt) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                hmac(challengeKey, "challenge-v1:" + articleId + ":" + issuedAt + ":" + UUID.randomUUID()));
    }

    public String newVisitorToken() {
        byte[] token = new byte[32];
        random.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    public String newDeleteToken() {
        byte[] token = new byte[32];
        random.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private static byte[] derive(byte[] masterKey, String purpose) {
        return hmac(masterKey, "haoblog-comment-key:" + purpose);
    }

    private static byte[] hmac(byte[] hmacKey, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to hash comment security value", exception);
        }
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash comment security value", exception);
        }
    }

    public record EmailCiphertext(int keyVersion, byte[] nonce, byte[] ciphertext) {
        public EmailCiphertext {
            if (nonce == null || nonce.length != NONCE_BYTES || ciphertext == null) {
                throw new IllegalArgumentException("Invalid email ciphertext");
            }
            nonce = nonce.clone();
            ciphertext = ciphertext.clone();
        }

        @Override public byte[] nonce() { return nonce.clone(); }
        @Override public byte[] ciphertext() { return ciphertext.clone(); }
    }
}
