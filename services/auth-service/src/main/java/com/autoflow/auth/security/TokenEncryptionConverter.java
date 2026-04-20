package com.autoflow.auth.security;

import com.autoflow.auth.config.AppProperties;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter that encrypts OAuth provider tokens at rest using
 * AES-256-CBC with HMAC via spring-security-crypto's Encryptors.text().
 *
 * The encryption key is read from app.encryption.secret (env: ENCRYPTION_SECRET).
 * The salt is read from app.encryption.salt (env: ENCRYPTION_SALT), a 16-byte hex string.
 *
 * Null values pass through unchanged so nullable refreshToken columns
 * are stored as NULL rather than an encrypted empty string.
 *
 * autoApply = false (default) means only columns explicitly annotated with
 * {@code @Convert(converter = TokenEncryptionConverter.class)} are encrypted.
 */
@Component
@Converter
public class TokenEncryptionConverter implements AttributeConverter<String, String> {

    private final TextEncryptor encryptor;

    public TokenEncryptionConverter(AppProperties appProperties) {
        AppProperties.Encryption enc = appProperties.getEncryption();
        this.encryptor = Encryptors.text(enc.getSecret(), enc.getSalt());
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return encryptor.decrypt(dbData);
    }
}
