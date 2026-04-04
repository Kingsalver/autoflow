package com.autoflow.auth.repository;

import com.autoflow.auth.entity.OAuthToken;
import com.autoflow.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthTokenRepository extends JpaRepository<OAuthToken, UUID> {
    Optional<OAuthToken> findByUserAndProvider(User user, String provider);
    void deleteByUserAndProvider(User user, String provider);
}