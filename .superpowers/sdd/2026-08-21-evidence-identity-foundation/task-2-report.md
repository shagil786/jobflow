# Task 2 Report: Expand Gmail message identity and thread persistence

Date: 2026-08-21
Worktree: `/Users/mdshagilnizami/Documents/jobs/jobflow/.worktrees/evidence-identity-foundation`
Branch: `evidence-identity-foundation`

## Scope completed

Implemented Task 2 only for the ingestion service:

- Expanded `GmailMessageMetadata` to carry `connectionId`, `tenantId`, `userId`, `messageId`, `threadId`, `sender`, `replyTo`, `recipients`, `subject`, `receivedAt`, `labelIds`, and `normalizedContentHash`.
- Preserved the existing `GmailMessageStore.saveIfAbsent(...)` public method while adding owner-scoped lookup support through `findByProviderIdentity(...)`.
- Changed Gmail message persistence to use provider identity `(connectionId, messageId)` instead of globally keying on `messageId`.
- Added Gmail thread persistence with `GmailThreadStore.saveIfAbsent(UUID connectionId, String tenantId, String userId, String threadId)`.
- Added Flyway migrations `V4__expand_gmail_message_metadata.sql` and `V5__create_gmail_threads.sql`.
- Kept the Gmail connection and encrypted refresh-token behavior intact.

## TDD flow

1. Added failing red tests:
   - `GmailMessageStoreTest`
   - `GmailThreadStoreTest`
   - `FlywayMigrationTest`
2. Ran:
   - `mvn -q -Dtest=GmailMessageStoreTest,GmailThreadStoreTest test`
3. Observed the expected red state:
   - missing thread store/repository/types
   - missing owner-scoped message lookup
   - outdated `GmailMessageMetadata` contract
4. Implemented the minimal persistence and migration changes to satisfy Task 2.
5. Ran the focused green suite:
   - `mvn -q -Dtest=GmailMessageStoreTest,GmailThreadStoreTest,GmailConnectionServiceTest,FlywayMigrationTest test`

## Key implementation details

### Gmail messages

- `GmailMessageEntity` now uses an embedded composite key `(connectionId, messageId)`.
- Owner data (`tenantId`, `userId`) is stored on the message row and used in repository queries where available.
- `JpaGmailMessageStore.saveIfAbsent(...)` stays idempotent for the same owner/provider identity.
- If the same provider identity is reused under a different owner, the store throws:
  - `IllegalStateException("gmail message identity already belongs to a different owner")`

### Gmail threads

- Added:
  - `GmailThreadEntity`
  - `GmailThreadRepository`
  - `GmailThreadStore`
  - `JpaGmailThreadStore`
- Threads are keyed by `(connectionId, threadId)`.
- `saveIfAbsent(...)` returns the canonical persisted `GmailThreadRecord`.

### Migrations

- `V4__expand_gmail_message_metadata.sql`
  - rebuilds `gmail_messages` into a composite-key table
  - carries forward existing rows
  - backfills `tenant_id` and `user_id` from `gmail_connections`
  - maps legacy `internal_date` into `received_at`
  - adds the owner index
- `V5__create_gmail_threads.sql`
  - creates `gmail_threads`
  - adds owner indexing

### Test harness fix

- The focused JPA tests initially failed on JDK 26 because Mockito’s inline mock maker attempted VM self-attachment during Spring Boot test listeners.
- Added:
  - `services/ingestion-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- Value:
  - `mock-maker-subclass`
- This keeps the Task 2 test suite runnable in the current environment without changing application behavior.

## Files changed

Modified:

- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailMessageEntity.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailMessageMetadata.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailMessageRepository.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailMessageStore.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailSyncService.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/JpaGmailMessageStore.java`
- `services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailSyncServiceTest.java`

Added:

- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailThreadEntity.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailThreadRepository.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailThreadStore.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/JpaGmailThreadStore.java`
- `services/ingestion-service/src/main/resources/db/migration/V4__expand_gmail_message_metadata.sql`
- `services/ingestion-service/src/main/resources/db/migration/V5__create_gmail_threads.sql`
- `services/ingestion-service/src/test/java/dev/jobflow/ingestion/FlywayMigrationTest.java`
- `services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailMessageStoreTest.java`
- `services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailThreadStoreTest.java`
- `services/ingestion-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`

## Verification result

Passed:

- `mvn -q -Dtest=GmailMessageStoreTest,GmailThreadStoreTest,GmailConnectionServiceTest,FlywayMigrationTest test`

Notes:

- The suite prints a Mockito/JDK 26 warning about inline self-attachment, but the subclass mock-maker override prevents it from failing the focused run.
- No later-task APIs or files outside this worktree were modified.
