package dev.jobflow.contacts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public record DiscoveredContact(String name, String role, String company, String email, String profileUrl,
                                String sourceUrl, ContactSourceType sourceType, String evidence, String messageId,
                                String threadId, double confidence, ContactStatus status, String verificationStatus,
                                String provider, String providerVersion, AllowedUse allowedUse, String contentHash) {
  public static DiscoveredContact of(String name, String role, String company, String email, String profileUrl,
                                     String sourceUrl, ContactSourceType sourceType, String evidence, String messageId,
                                     String threadId, double confidence, ContactStatus status, String verificationStatus,
                                     String provider, String providerVersion, AllowedUse allowedUse) {
    String raw = String.join("|", nullToEmpty(email), nullToEmpty(profileUrl), nullToEmpty(sourceUrl), nullToEmpty(messageId), evidence);
    try { return new DiscoveredContact(name, role, company, email, profileUrl, sourceUrl, sourceType, evidence, messageId, threadId, confidence, status, verificationStatus, provider, providerVersion, allowedUse, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)))); }
    catch (Exception exception) { throw new IllegalStateException("unable to hash contact evidence", exception); }
  }
  private static String nullToEmpty(String value) { return value == null ? "" : value; }
}
