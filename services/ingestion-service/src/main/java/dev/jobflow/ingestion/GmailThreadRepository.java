package dev.jobflow.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailThreadRepository extends JpaRepository<GmailThreadEntity, GmailThreadEntity.GmailThreadId> {}
