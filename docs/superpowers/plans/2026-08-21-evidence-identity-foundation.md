# Evidence and Identity Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist safe Gmail message/thread evidence and deterministic identity candidates so later classification can produce auditable suggestions without guessing.

**Architecture:** Extend the ingestion service to fetch scoped Gmail message metadata and short-lived sanitized content, persist tenant-owned message/thread records with idempotent provider identities, and expose a pure normalization/evidence API. No AI provider, application mutation, review acceptance, or next-action calculation is included in this plan.

**Tech Stack:** Java 17+ / Spring Boot / Spring Data JPA / Flyway / H2 and PostgreSQL; TypeScript contracts; JUnit 5 / AssertJ; Vitest.

**Spec:** `docs/superpowers/specs/2026-08-21-gmail-classification-design.md`

## Global Constraints

- AI suggests; the user confirms.
- Unknown and conflicted are valid outcomes.
- Every extracted fact must point to evidence from a specific message.
- Gmail scope remains `JobFlow/Track` only.
- Raw sanitized body content is encrypted and retained for 14 days by default.
- Attachments are not analyzed in v1.
- No raw bodies, prompts, tokens, or sensitive excerpts appear in logs or traces.
- Every persisted record is tenant/user owned and cross-tenant lookups are rejected.
- Duplicate Gmail messages are idempotent by `tenantId + userId + connectionId + gmailMessageId`.
- Every task ends with its focused tests passing before the next task starts.

## File Map

- Modify `contracts/src/index.ts` to define versioned evidence, message direction, identity candidates, and safe Gmail metadata contracts.
- Modify `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailApiClient.java` to fetch scoped message metadata and normalized content inputs.
- Modify `services/ingestion-service/src/main/java/dev/jobflow/ingestion/RestGmailApiClient.java` to request Gmail `format=metadata` plus the required headers and `format=full` only for short-lived body normalization.
- Modify `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailMessageMetadata.java` and `GmailMessageEntity.java` to store safe message metadata and content hashes.
- Create `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailThreadEntity.java` and `GmailThreadRepository.java` for tenant-owned thread records.
- Create `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailThreadStore.java` and `JpaGmailThreadStore.java` for thread persistence.
- Create `services/ingestion-service/src/main/java/dev/jobflow/ingestion/EmailNormalizer.java` for deterministic HTML/quoted-history normalization.
- Create `services/ingestion-service/src/main/java/dev/jobflow/ingestion/IdentityCandidateExtractor.java` for company, role, date, and contact candidates with evidence references.
- Create `services/ingestion-service/src/main/java/dev/jobflow/ingestion/SafeGmailMessage.java` as the normalized boundary object; it must not expose OAuth tokens.
- Create `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailEvidenceService.java` to orchestrate normalization and persistence without invoking an LLM.
- Create `services/ingestion-service/src/main/resources/db/migration/V4__expand_gmail_message_metadata.sql` and `V5__create_gmail_threads.sql`.
- Add focused tests beside each Java unit under `services/ingestion-service/src/test/java/dev/jobflow/ingestion/`.
- Add `contracts/src/index.test.ts` only if the contract package already has a test runner; otherwise validate with the existing web TypeScript check.

---

### Task 1: Define the versioned evidence and identity contracts

**Files:**
- Modify: `contracts/src/index.ts`
- Test: `contracts/src/index.test.ts` if the package has an existing test runner; otherwise `web/lib/contracts.test.ts`

**Interfaces:**
- Produces `MessageDirection`, `MessageIntent`, `EvidenceSpanV1`, `ExtractedFieldCandidate`, `SafeGmailMessageV1`, and `ClassificationSuggestionV1` types for later provider and review plans.
- Does not remove the existing public draft and application contracts.

- [ ] **Step 1: Write the failing contract assertions**

Assert that the new types represent:

```ts
type MessageDirection = "INBOUND" | "OUTBOUND" | "UNKNOWN";
type MessageIntent =
  | "APPLICATION_CONFIRMATION" | "RECRUITER_OUTREACH" | "RECRUITER_REPLY"
  | "INTERVIEW_INVITATION" | "INTERVIEW_RESCHEDULE" | "INTERVIEW_FEEDBACK"
  | "REJECTION" | "OFFER" | "WITHDRAWAL" | "FOLLOW_UP_REQUEST"
  | "EMPLOYER_UPDATE" | "UNRELATED" | "UNKNOWN";

interface EvidenceSpanV1 {
  evidenceId: string;
  messageId: string;
  threadId: string;
  source: "subject" | "sender" | "recipient" | "body" | "header";
  quotedText?: string;
  normalizedTextHash: string;
  sourceAvailable: boolean;
}
```

- [ ] **Step 2: Run the contract typecheck**

Run `npm run typecheck` from `web/`.

Expected: FAIL because the new exported types do not exist.

- [ ] **Step 3: Add the minimal exported types**

Add the exact unions and interfaces above, plus:

```ts
export interface ExtractedFieldCandidate<T> {
  value?: T;
  confidence: number;
  evidence: EvidenceSpanV1[];
  source: "capture" | "header" | "body" | "ai" | "user";
  requiresReview: boolean;
  conflict: boolean;
}

export interface SafeGmailMessageV1 {
  tenantId: string;
  userId: string;
  connectionId: string;
  messageId: string;
  threadId: string;
  sender?: string;
  replyTo?: string;
  recipients: string[];
  subject?: string;
  receivedAt?: string;
  labelIds: string[];
  normalizedContentHash?: string;
}

export interface ClassificationSuggestionV1 {
  suggestionId: string;
  messageId: string;
  threadId: string;
  intent: MessageIntent;
  direction: MessageDirection;
  company?: ExtractedFieldCandidate<string>;
  role?: ExtractedFieldCandidate<string>;
  applicationDate?: ExtractedFieldCandidate<string>;
  contact?: ExtractedFieldCandidate<string>;
  confidence: number;
  evidence: EvidenceSpanV1[];
  missingFields: string[];
  contradictions: string[];
  requiresReview: true;
  classifierVersion: string;
  contentHash: string;
}
```

Keep `ClassificationSuggestion` backward-compatible until the review API plan replaces it with a versioned endpoint.

- [ ] **Step 4: Run the typecheck again**

Run `npm run typecheck` from `web/` and `npm test -- --run`.

Expected: PASS with all existing tests unchanged.

### Task 2: Expand Gmail message identity and thread persistence

**Files:**
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailThreadEntity.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailThreadRepository.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailThreadStore.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/JpaGmailThreadStore.java`
- Modify: `GmailMessageMetadata.java`, `GmailMessageEntity.java`, `GmailMessageRepository.java`, `JpaGmailMessageStore.java`
- Create: `services/ingestion-service/src/main/resources/db/migration/V4__expand_gmail_message_metadata.sql`
- Create: `services/ingestion-service/src/main/resources/db/migration/V5__create_gmail_threads.sql`
- Test: `GmailMessageStoreTest.java`, `GmailThreadStoreTest.java`

**Interfaces:**
- `GmailMessageMetadata` must include `connectionId`, `tenantId`, `userId`, `messageId`, `threadId`, `sender`, `replyTo`, `recipients`, `subject`, `receivedAt`, `labelIds`, and `normalizedContentHash`.
- `GmailThreadStore.saveIfAbsent(UUID connectionId, String tenantId, String userId, String threadId)` returns the canonical thread record.
- `GmailMessageStore.saveIfAbsent(GmailMessageMetadata message)` remains idempotent and rejects an existing message whose connection, tenant, or user differs.

- [ ] **Step 1: Add failing persistence tests**

Test that:

```java
assertThat(store.saveIfAbsent(first)).isTrue();
assertThat(store.saveIfAbsent(first)).isFalse();
assertThat(store.findByProviderIdentity(tenantId, userId, connectionId, messageId)).isPresent();
```

Also test that the same Gmail `messageId` under a different tenant is not returned by the first tenant’s lookup.

- [ ] **Step 2: Run the ingestion tests**

Run `mvn -q -Dtest=GmailMessageStoreTest,GmailThreadStoreTest test` from `services/ingestion-service`.

Expected: FAIL because the new stores and fields are missing.

- [ ] **Step 3: Add Flyway migrations**

`V4__expand_gmail_message_metadata.sql` adds nullable metadata columns first so existing rows remain migratable:

```sql
alter table gmail_messages add column if not exists tenant_id varchar(120);
alter table gmail_messages add column if not exists user_id varchar(120);
alter table gmail_messages add column if not exists sender varchar(320);
alter table gmail_messages add column if not exists reply_to varchar(320);
alter table gmail_messages add column if not exists recipients varchar(2048);
alter table gmail_messages add column if not exists subject varchar(1024);
alter table gmail_messages add column if not exists received_at timestamp with time zone;
alter table gmail_messages add column if not exists normalized_content_hash varchar(128);
create index if not exists ix_gmail_messages_owner on gmail_messages (tenant_id, user_id, connection_id);
```

`V5__create_gmail_threads.sql` creates tenant-owned threads:

```sql
create table if not exists gmail_threads (
  thread_id varchar(255) not null,
  connection_id uuid not null,
  tenant_id varchar(120) not null,
  user_id varchar(120) not null,
  first_seen_at timestamp with time zone not null,
  last_seen_at timestamp with time zone not null,
  primary key (connection_id, thread_id),
  constraint fk_gmail_threads_connection foreign key (connection_id) references gmail_connections(connection_id)
);
create index if not exists ix_gmail_threads_owner on gmail_threads (tenant_id, user_id);
```

- [ ] **Step 4: Implement entities and stores**

Use `(connectionId, messageId)` as the provider identity and `(connectionId, threadId)` as the thread identity. Every repository query must include tenant and user ownership where those fields are available. Do not make Gmail IDs globally addressable.

- [ ] **Step 5: Run focused and migration tests**

Run `mvn -q -Dtest=GmailMessageStoreTest,GmailThreadStoreTest,GmailConnectionServiceTest,FlywayMigrationTest test`.

Expected: PASS, including migration validation and existing encrypted-token tests.

### Task 3: Fetch only safe Gmail metadata and bounded content

**Files:**
- Modify: `GmailApiClient.java`, `RestGmailApiClient.java`, `GmailSyncService.java`
- Create: `SafeGmailMessage.java`
- Test: `RestGmailApiClientTest.java`, `GmailSyncServiceTest.java`

**Interfaces:**
- `GmailApiClient.fetchMessageMetadata(String accessToken, String messageId)` returns sender, reply-to, recipients, subject, received time, thread ID, and labels.
- `GmailApiClient.fetchMessageBodyForProcessing(String accessToken, String messageId)` returns sanitized input with a maximum decoded size of 256 KiB and no attachment bytes.
- `SafeGmailMessage` contains no access token, refresh token, raw HTML, or attachment payload.

- [ ] **Step 1: Add failing client tests**

Test that the request includes `format=metadata` and only these headers:

```text
From
Reply-To
To
Cc
Subject
Date
Message-ID
In-Reply-To
References
```

Test that oversized body content is rejected before persistence and attachment MIME parts are ignored.

- [ ] **Step 2: Implement metadata/body DTOs and bounded fetches**

Use Gmail message metadata format for normal sync. Fetch full body only during classification preparation, sanitize HTML to plain text, cap normalized text at 256 KiB, and calculate SHA-256 over normalized text. Never log the body or token.

- [ ] **Step 3: Connect sync to metadata persistence**

For each message reference from the existing label-scoped full or incremental sync:

1. Fetch metadata.
2. Upsert the tenant-owned thread.
3. Insert message metadata idempotently.
4. Advance the Gmail cursor only after all message metadata writes succeed.

If one message fails, return a retryable sync failure and leave the previous cursor unchanged.

- [ ] **Step 4: Run the focused sync tests**

Run `mvn -q -Dtest=RestGmailApiClientTest,GmailSyncServiceTest test`.

Expected: PASS for label-scoped full recovery, incremental cursor handling, duplicates, metadata persistence, and cursor non-advancement on failure.

### Task 4: Add deterministic normalization and identity candidates

**Files:**
- Create: `EmailNormalizer.java`
- Create: `IdentityCandidateExtractor.java`
- Create: `GmailEvidenceService.java`
- Test: `EmailNormalizerTest.java`, `IdentityCandidateExtractorTest.java`, `GmailEvidenceServiceTest.java`

**Interfaces:**
- `EmailNormalizer.normalize(String htmlOrText)` returns `{ plainText, quotedText, contentHash }`.
- `IdentityCandidateExtractor.extract(SafeGmailMessage message)` returns candidates for company, role, application date, and contact, each with evidence spans and `requiresReview`.
- `GmailEvidenceService.prepare(UUID connectionId, String messageId)` returns a safe evidence bundle and never changes application status.

- [ ] **Step 1: Add failing normalization tests**

Cover:

- HTML tags removed while visible text is preserved.
- Script/style content removed.
- Common quoted-reply blocks separated from new content.
- Empty or malformed content returns a valid empty normalized result.
- Input over 256 KiB is rejected.

- [ ] **Step 2: Add failing identity-candidate tests**

Cover:

- `jobs@acme.com` supports an Acme company candidate but does not auto-confirm it alone.
- `no-reply@greenhouse.io` produces no company candidate without employer evidence.
- Explicit “application submitted on March 4” produces an application-date candidate with body evidence.
- Gmail received time is not emitted as a confirmed application date.
- A header address produces a contact candidate; a name without an address does not.
- Forwarded blocks produce separate outer/original evidence and require review.

- [ ] **Step 3: Implement pure normalization and extraction**

Use deterministic parsing only. Do not call Bedrock or Azure. Candidate confidence must be bounded to `[0,1]`, and every non-empty candidate must include at least one evidence span.

- [ ] **Step 4: Implement evidence preparation**

Load one tenant-owned message, fetch bounded body content only when required, normalize it, create short evidence spans with content hash and `sourceAvailable=true`, and return `UNKNOWN`/`requiresReview=true` when evidence is insufficient.

- [ ] **Step 5: Run the complete ingestion suite**

Run `mvn test -q` from `services/ingestion-service`.

Expected: PASS with no raw message body in test output or application logs.

### Task 5: Contract and operational verification

**Files:**
- Modify: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailConnectionController.java` only if a metadata/evidence status endpoint is needed by the existing web route.
- Create: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailEvidenceControllerTest.java` if an endpoint is added.
- Modify: `docs/jobflow-design.md` only to link this plan and record the 14-day raw-content default.

- [ ] **Step 1: Verify tenant and authorization boundaries**

Test wrong internal key → `401`, missing key → controlled `400/401`, unknown connection → non-success without revealing whether another tenant owns it, and cross-tenant message lookup → not found.

- [ ] **Step 2: Verify retention and logging constraints**

Search source and test output for raw body logging. Confirm only hashes, IDs, counts, statuses, and correlation identifiers are logged.

- [ ] **Step 3: Run all affected checks**

Run:

```bash
cd services/ingestion-service && mvn test -q
cd ../../web && npm test -- --run && npm run typecheck
```

Then smoke-test:

```bash
curl -sS http://localhost:8082/actuator/health
curl -sS -X POST -H 'X-Internal-Service-Key: wrong' \
  http://localhost:8082/internal/v1/gmail/connections/00000000-0000-0000-0000-000000000000/sync
```

Expected: ingestion health `UP`; invalid sync key `401`.

## Deferred follow-up plans

The following are intentionally separate plans because each changes independent interfaces and has its own review gate:

1. Deterministic message-intent classifier and versioned suggestion persistence.
2. Bedrock/Azure provider adapters, JSON-schema validation, and evidence validation.
3. Human review queue, acceptance events, application-link suggestions, and merge/split workflow.
4. Rule-based next-action engine and dashboard evidence UI.
5. Accuracy evaluation set, calibration, feature flags, and limited automation approval.
