package com.autoflow.auth.entity;

import com.autoflow.auth.security.TokenEncryptionConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "oauth_tokens", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider"}))
@Data
@EqualsAndHashCode(of = "id")
@ToString(exclude = "user")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String provider;   // "github" or "google"

    @Convert(converter = TokenEncryptionConverter.class)
    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Convert(converter = TokenEncryptionConverter.class)
    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}