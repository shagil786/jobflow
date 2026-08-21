package dev.jobflow.identity;

public interface TokenCipher {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
