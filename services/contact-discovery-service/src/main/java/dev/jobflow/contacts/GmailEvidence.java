package dev.jobflow.contacts;

public record GmailEvidence(String messageId, String threadId, String sender, String subject, String excerpt, String observedAt) {}
