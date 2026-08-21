package dev.jobflow.identity;

import java.time.Instant;

record CreateSessionCommand(String userId, String tenantId, String provider, String accessToken,
        String refreshToken, Instant accessTokenExpiresAt) {}
record SessionRecord(String sessionId, String userId, String tenantId, String provider, Instant expiresAt) {}
record AccessTokenResult(String accessToken, Instant expiresAt) {}
record SessionMetadata(String userId, String tenantId) {}
record RefreshedTokens(String accessToken, String refreshToken, long expiresInSeconds) {}
