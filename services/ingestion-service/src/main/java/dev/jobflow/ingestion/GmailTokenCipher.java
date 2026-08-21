package dev.jobflow.ingestion;

public interface GmailTokenCipher {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
