package com.example.cube.auth.service;

import com.example.cube.auth.entity.RevokedToken;
import com.example.cube.auth.repository.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/** Server-side logout: a revoked token id is rejected until the token would have expired anyway. */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private final RevokedTokenRepository repository;

    public TokenRevocationService(RevokedTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void revoke(String tokenId, String username, Date expiresAt) {
        if (tokenId == null || repository.existsById(tokenId)) {
            return;
        }
        RevokedToken revoked = new RevokedToken();
        revoked.setTokenId(tokenId);
        revoked.setUsername(username);
        revoked.setExpiresAt(expiresAt == null
                ? LocalDateTime.now().plusDays(1)
                : LocalDateTime.ofInstant(expiresAt.toInstant(), ZoneId.systemDefault()));
        revoked.setRevokedAt(LocalDateTime.now());
        repository.save(revoked);
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String tokenId) {
        return repository.existsById(tokenId);
    }

    /** Hourly cleanup of revocations whose tokens have expired. */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 300_000L)
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpired(LocalDateTime.now());
        if (removed > 0) {
            log.info("Purged {} expired revoked tokens", removed);
        }
    }
}
