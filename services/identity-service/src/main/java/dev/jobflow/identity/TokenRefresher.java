package dev.jobflow.identity;

public interface TokenRefresher {
    RefreshedTokens refresh(String refreshToken);
}
