# Gmail Automatic Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a real, user-authorized Gmail backfill that prioritizes the newest day, processes older mail in durable seven-day SQS batches, classifies evidence for review, and exposes quiet-background progress in the JobFlow dashboard.

**Architecture:** Add durable backfill/run/batch persistence to ingestion-service, publish only identifiers to AWS SQS (LocalStack locally), and process each batch through metadata import, deterministic candidate filtering, bounded enrichment, and reviewable classification. Add authenticated Next.js proxy routes and a dashboard status/review experience; preserve the existing incremental history sync as the steady-state path.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Spring Data JPA, Flyway, AWS SDK v2 SQS, H2/PostgreSQL, Next.js 15 App Router, React 19, TypeScript, Vitest, Spring MockMvc/JUnit.

**Spec:** `docs/superpowers/specs/2026-08-21-gmail-auto-import-design.md`

## Global Constraints

- Gmail OAuth remains read-only; do not create labels, move messages, modify filters, or send mail.
- Queue payloads contain identifiers and window metadata only; never tokens, raw bodies, resumes, or prompts.
- All reads/writes enforce tenant ID and user ID at the service and repository boundary.
- Uncertain suggestions stay in review; only confirmed evidence creates or updates applications, timelines, reminders, or contacts.
- Raw message content is bounded, sanitized, short-lived, and never logged or traced.
- Every mutating API accepts and persists an idempotency key; errors use the standard `{ error: { code, message, details? } }` envelope.
- Every backend stage ends with Maven tests; every frontend stage ends with Vitest, typecheck, and browser verification where applicable.
- Preserve the unrelated existing `web/tsconfig.tsbuildinfo` modification.

## File Map

Create or modify only these focused areas:

- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/`: backfill records, repositories, window planner, SQS adapter/worker, automatic Gmail import, candidate pipeline, and internal controllers.
- `services/ingestion-service/src/main/resources/db/migration/`: Flyway migrations V12 onward for runs, batches, candidates, and review links.
- `services/ingestion-service/src/test/java/dev/jobflow/ingestion/`: unit, persistence, MockMvc, queue, and failure-path tests for each backend unit.
- `contracts/src/index.ts` and `contracts/events/`: versioned TypeScript types and queue/event schemas.
- `web/app/api/gmail/`: authenticated backfill/status/review proxy routes.
- `web/app/page.tsx`, `web/app/globals.css`, and focused `web/components/` files: quiet import status, review queue, and responsive states. Split the current large page only where a component has one responsibility.
- `infra/localstack/` and `.env.example`: LocalStack SQS queues and explicit local configuration.
- `README.md` and `services/README.md`: replace label-required onboarding instructions with automatic backfill setup.

---

### Task 1: Version the contracts and persistence model

**Files:**
- Create: `contracts/events/gmail-backfill-requested.v1.json`
- Create: `contracts/events/gmail-backfill-batch.v1.json`
- Modify: `contracts/src/index.ts`
- Create: `services/ingestion-service/src/main/resources/db/migration/V12__create_gmail_backfill_runs.sql`
- Create: `services/ingestion-service/src/main/resources/db/migration/V13__create_gmail_backfill_batches.sql`
- Create: `services/ingestion-service/src/main/resources/db/migration/V14__create_gmail_message_candidates.sql`
- Create: `services/ingestion-service/src/main/resources/db/migration/V15__link_review_items_to_candidates.sql`
- Test: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/FlywayMigrationTest.java`

**Interfaces:**
- Produces `BackfillRunStatus`, `BackfillBatchStatus`, `BackfillMode`, `GmailBackfillRequestedV1`, and `GmailBackfillBatchV1` types.
- Produces database tables keyed by tenant, user, connection, run, batch, and provider message identity for later tasks.

- [ ] **Step 1: Write failing contract and migration tests**

Add assertions that the event JSON requires `runId`, `batchId`, `connectionId`, tenant/user IDs, UTC window bounds, correlation ID, and attempt ID; assert Flyway creates the four new tables, unique provider identity, active-run uniqueness, batch window ordering, and review link columns.

- [ ] **Step 2: Run the focused migration test**

Run: `mvn -pl ingestion-service -Dtest=FlywayMigrationTest test`

Expected: FAIL because V12–V15 and the contract types do not exist.

- [ ] **Step 3: Add the versioned types and migrations**

Use `uuid`/`varchar` ownership columns, `timestamptz`-compatible `timestamp` fields already used by the service, explicit status strings, JSON/text for deterministic signals, and indexes for `(tenant_id,user_id,status)`, `(connection_id,status)`, `(run_id,sequence_no)`, and `(connection_id,provider_message_id)`.

The active-run uniqueness rule must be a partial unique index for statuses `queued`, `running`, `pausing`, `paused`, `cancelling`; terminal statuses must not block a future run.

- [ ] **Step 4: Run migration and contract tests**

Run: `mvn -pl ingestion-service -Dtest=FlywayMigrationTest test` and `npm --prefix contracts test`.

Expected: PASS with no raw message body or token columns added.

- [ ] **Step 5: Commit**

```bash
git add contracts services/ingestion-service/src/main/resources/db/migration services/ingestion-service/src/test/java/dev/jobflow/ingestion/FlywayMigrationTest.java
git commit -m "Add Gmail backfill contracts and migrations"
```

### Task 2: Implement batch-window planning and durable run state

**Files:**
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailBackfillModels.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailBackfillRunEntity.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailBackfillBatchEntity.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailBackfillRepository.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailBackfillBatchRepository.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailBackfillWindowPlanner.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailBackfillService.java`
- Test: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailBackfillWindowPlannerTest.java`
- Test: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailBackfillServiceTest.java`

**Interfaces:**
- `GmailBackfillWindowPlanner.plan(Instant from, Instant to): List<BackfillWindow>` returns one newest-day priority window and then non-overlapping seven-day windows ordered newest to oldest.
- `GmailBackfillService.start(BackfillRequest, OwnerContext, String idempotencyKey): BackfillRunRecord` creates or replays a run.
- `GmailBackfillService.status(UUID runId, OwnerContext): BackfillRunStatus` returns progress only for the owning tenant/user.
- `pause`, `resume`, `cancel`, and `extend` mutate only non-terminal owned runs.

- [ ] **Step 1: Write failing planner tests**

Test a two-month range ending at `2026-08-21T12:00:00Z`: assert the first window is the final one-day interval, every later interval is at most seven days, windows are contiguous and non-overlapping, and the oldest window ends exactly at `from`.

- [ ] **Step 2: Run planner tests to verify failure**

Run: `mvn -pl ingestion-service -Dtest=GmailBackfillWindowPlannerTest test`

Expected: FAIL because planner/models are absent.

- [ ] **Step 3: Implement planner and state transitions**

Validate UTC range, default omitted range to two months, reject `from >= to`, reject unsupported batch size, and reject a second active run with `GMAIL_BACKFILL_ALREADY_ACTIVE`. Store counters transactionally and expose allowed actions from status.

- [ ] **Step 4: Add service tests**

Cover idempotent replay, tenant isolation, pause/resume/cancel transitions, earlier-date extension prepending batches, terminal-run extension rejection, and no deletion of imported evidence on cancellation.

- [ ] **Step 5: Run backend tests**

Run: `mvn -pl ingestion-service -Dtest=GmailBackfillWindowPlannerTest,GmailBackfillServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailBackfill* services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailBackfill* 
git commit -m "Add durable Gmail backfill state and window planning"
```

### Task 3: Add SQS/LocalStack queue delivery and worker lifecycle

**Files:**
- Modify: `services/pom.xml`
- Modify: `services/ingestion-service/pom.xml`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/BackfillQueue.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/SqsBackfillQueue.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/SqsBackfillWorker.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/SqsConfiguration.java`
- Create: `infra/localstack/docker-compose.yml`
- Modify: `.env.example`
- Test: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/SqsBackfillQueueTest.java`
- Test: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/SqsBackfillWorkerTest.java`

**Interfaces:**
- `BackfillQueue.publish(GmailBackfillBatchV1): void`
- `BackfillQueue.receive(): List<QueuedBatch>`
- `BackfillQueue.acknowledge(Receipt): void`
- `BackfillQueue.retry(Receipt, Duration visibilityTimeout): void`
- `SqsBackfillWorker.process(QueuedBatch): void`

- [ ] **Step 1: Add the AWS SDK and queue configuration tests**

Test that the queue client uses the configured endpoint/region, that production configuration does not require a LocalStack endpoint, and that queue names are explicit for main and DLQ queues.

- [ ] **Step 2: Run queue tests to verify failure**

Run: `mvn -pl ingestion-service -Dtest=SqsBackfillQueueTest,SqsBackfillWorkerTest test`

Expected: FAIL because the adapter and worker do not exist.

- [ ] **Step 3: Implement the queue adapter**

Pin one AWS SDK v2 BOM version in `services/pom.xml`, use `SqsClient`, serialize the versioned batch event, configure endpoint override only when `JOBFLOW_SQS_ENDPOINT` is present, and set visibility timeout/dead-letter redrive parameters from environment. Never serialize refresh tokens or message content.

- [ ] **Step 4: Implement durable worker behavior**

Claim a queued batch atomically, process it through the importer interface defined in Task 4, update attempts and safe failure codes, acknowledge only after terminal success, and leave a retryable batch visible for retry. After the configured attempt limit, mark `dead_lettered` and publish/retain the DLQ record.

- [ ] **Step 5: Run backend tests and LocalStack smoke test**

Run: `mvn -pl ingestion-service -Dtest=SqsBackfillQueueTest,SqsBackfillWorkerTest test`; then start LocalStack with `docker compose -f infra/localstack/docker-compose.yml up -d` and run the documented queue smoke command.

Expected: duplicate deliveries are idempotent, failures retry, and exhausted messages appear in the DLQ.

- [ ] **Step 6: Commit**

```bash
git add services/pom.xml services/ingestion-service/pom.xml services/ingestion-service/src/main/java/dev/jobflow/ingestion/Sqs* services/ingestion-service/src/main/java/dev/jobflow/ingestion/BackfillQueue.java infra/localstack .env.example services/ingestion-service/src/test/java/dev/jobflow/ingestion/Sqs*
git commit -m "Add durable SQS backfill worker"
```

### Task 4: Implement automatic Gmail date-window import and candidate filtering

**Files:**
- Modify: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailApiClient.java`
- Modify: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/RestGmailApiClient.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailAutomaticImportService.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailMessageCandidateEntity.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailMessageCandidateRepository.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailCandidateFilter.java`
- Modify: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailSyncService.java`
- Test: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailAutomaticImportServiceTest.java`
- Test: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailCandidateFilterTest.java`
- Test: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/RestGmailApiClientTest.java`

**Interfaces:**
- `GmailApiClient.listMessages(String accessToken, String query, String pageToken): MessagePage` supports date-bounded queries without requiring a label.
- `GmailAutomaticImportService.importBatch(UUID batchId): ImportBatchResult` imports metadata, deduplicates messages, and persists candidate state.
- `GmailCandidateFilter.evaluate(GmailMessageMetadata): CandidateDecision` returns deterministic score, signals, and processing state.

- [ ] **Step 1: Write failing Gmail query/filter tests**

Assert date queries use `after:`/`before:` bounds, pagination is preserved, label lookup is not required in automatic mode, promotional/unsubscribe messages are excluded, recruiter/application/interview/rejection/offer signals become candidates, and generic sender domains do not become confirmed companies.

- [ ] **Step 2: Run focused tests to verify failure**

Run: `mvn -pl ingestion-service -Dtest=GmailAutomaticImportServiceTest,GmailCandidateFilterTest,RestGmailApiClientTest test`

Expected: FAIL because automatic query/import and candidate persistence do not exist.

- [ ] **Step 3: Implement metadata-first import**

Refresh the encrypted token, query only the batch window, fetch metadata/snippet within the existing content limits, save thread/message metadata, create or update candidate rows by provider identity, and update batch counters. Keep prior cursor behavior for incremental sync and preserve the label-specific fallback only when explicitly requested.

- [ ] **Step 4: Add failure and retention tests**

Cover token expiry, Gmail 429/5xx retry classification, malformed dates, missing thread IDs, duplicate messages, partial page failure, HTML sanitization, maximum decoded bytes, and raw-body expiry without logging content.

- [ ] **Step 5: Run backend tests**

Run: `mvn -pl ingestion-service -Dtest=GmailAutomaticImportServiceTest,GmailCandidateFilterTest,RestGmailApiClientTest,GmailSyncServiceTest test`

Expected: PASS; automatic mode works without `JobFlow/Track`, and existing incremental tests remain green.

- [ ] **Step 6: Commit**

```bash
git add services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailApiClient.java services/ingestion-service/src/main/java/dev/jobflow/ingestion/RestGmailApiClient.java services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailAutomaticImportService.java services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailMessageCandidate* services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailCandidateFilter.java services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailSyncService.java services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailAutomaticImportServiceTest.java services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailCandidateFilterTest.java services/ingestion-service/src/test/java/dev/jobflow/ingestion/RestGmailApiClientTest.java
git commit -m "Import Gmail metadata without a required label"
```

### Task 5: Connect candidate enrichment, classification, and review acceptance

**Files:**
- Modify: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailClassificationService.java`
- Modify: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailEvidenceService.java`
- Modify: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/ClassificationSuggestionEntity.java`
- Modify: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/ClassificationReviewService.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/ClassifierProvider.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/ProviderNeutralClassifier.java`
- Modify: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/ClassificationReviewController.java`
- Test: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailClassificationServiceTest.java`
- Test: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/ClassificationReviewControllerTest.java`

**Interfaces:**
- `ClassifierProvider.classify(ClassificationInput): ClassificationSuggestionV1`
- `ProviderNeutralClassifier.classify(ClassificationInput): ClassificationSuggestionV1` selects the configured Azure OpenAI or Bedrock adapter.
- `ClassificationReviewService.accept(UUID suggestionId, OwnerContext, ReviewCorrection): ApplicationImportResult` creates/updates an application only when company and role are present after corrections.

- [ ] **Step 1: Write failing evidence/provider/review tests**

Assert provider selection is configuration-driven, classifier output contains confidence/evidence/missing fields, unsupported claims are not accepted, unknown company remains unknown, duplicate outreach is detected, and accept/correct/dismiss transitions are tenant-scoped and idempotent.

- [ ] **Step 2: Run tests to verify failure**

Run: `mvn -pl ingestion-service -Dtest=GmailClassificationServiceTest,ClassificationReviewControllerTest test`

Expected: FAIL for provider abstraction, candidate linkage, and confirmed-application rules.

- [ ] **Step 3: Implement provider-neutral classification**

Keep deterministic classification as the no-network baseline. Add Azure and Bedrock adapters behind explicit configuration; adapters receive only bounded candidate content and return the shared structured type. Redact prompts from logs and reject malformed provider output.

- [ ] **Step 4: Implement review linkage and acceptance**

Persist candidate ID, classifier version, evidence spans, and review state. On accept/correct, call the existing application import path with user corrections; on dismiss, preserve the audit record and prevent the same suggestion version from resurfacing.

- [ ] **Step 5: Run backend tests**

Run: `mvn -pl ingestion-service -Dtest=GmailClassificationServiceTest,ClassificationReviewControllerTest,GmailEvidenceServiceTest test`

Expected: PASS without requiring a live AI provider in unit tests.

- [ ] **Step 6: Commit**

```bash
git add services/ingestion-service/src/main/java/dev/jobflow/ingestion services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailClassificationServiceTest.java services/ingestion-service/src/test/java/dev/jobflow/ingestion/ClassificationReviewControllerTest.java
git commit -m "Add provider-neutral Gmail classification review"
```

### Task 6: Expose internal and authenticated web backfill APIs

**Files:**
- Modify: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailConnectionController.java`
- Create: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailBackfillController.java`
- Modify: `services/ingestion-service/src/main/java/dev/jobflow/ingestion/IngestionExceptionHandler.java`
- Create: `web/app/api/gmail/backfills/route.ts`
- Create: `web/app/api/gmail/backfills/[runId]/route.ts`
- Create: `web/app/api/gmail/backfills/[runId]/pause/route.ts`
- Create: `web/app/api/gmail/backfills/[runId]/resume/route.ts`
- Create: `web/app/api/gmail/backfills/[runId]/cancel/route.ts`
- Create: `web/app/api/gmail/backfills/[runId]/extend/route.ts`
- Create: `web/app/api/gmail/review-items/route.ts`
- Modify: `web/app/api/reviews/route.ts`
- Test: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailBackfillControllerTest.java`
- Test: `web/app/api/gmail/backfills/route.test.ts`
- Test: `web/app/api/gmail/review-items/route.test.ts`

**Interfaces:**
- Internal endpoints use `X-Internal-Service-Key`, owner fields from the authenticated web proxy, `Idempotency-Key`, and `X-Correlation-Id`.
- Browser endpoints obtain `getServerSessionMetadata()`, reject unauthenticated requests with `401`, proxy safe JSON, preserve `202/409/422/429`, and never accept tenant/user IDs from browser input.

- [ ] **Step 1: Write failing route/controller tests**

Test unauthorized internal keys, missing sessions, invalid ranges, duplicate active runs, `202` plus `Location`, tenant-scoped status, pause/resume/cancel/extend, cursor pagination, and the standard error envelope.

- [ ] **Step 2: Run tests to verify failure**

Run: `mvn -pl ingestion-service -Dtest=GmailBackfillControllerTest test` and `npm --prefix web test -- app/api/gmail/backfills/route.test.ts app/api/gmail/review-items/route.test.ts`.

Expected: FAIL because routes and controller are absent.

- [ ] **Step 3: Implement internal controller and error mapping**

Use request records with Jakarta validation, return `202` for commands, add `Location` for creation, map queue/provider/domain failures to safe stable codes, and include request/correlation IDs without raw provider details.

- [ ] **Step 4: Implement authenticated Next.js proxies**

Reuse existing service URL and internal-key helpers. Build owner query/body values server-side from session metadata. Forward only safe response bodies and set `cache: "no-store"` plus timeouts.

- [ ] **Step 5: Run backend and frontend route tests**

Run: `mvn -pl ingestion-service -Dtest=GmailBackfillControllerTest test`; `npm --prefix web test -- app/api/gmail/backfills/route.test.ts app/api/gmail/review-items/route.test.ts`; `npm --prefix web run typecheck`.

Expected: PASS with no browser-controlled tenant/user impersonation path.

- [ ] **Step 6: Commit**

```bash
git add services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailBackfillController.java services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailConnectionController.java services/ingestion-service/src/main/java/dev/jobflow/ingestion/IngestionExceptionHandler.java web/app/api/gmail/backfills web/app/api/gmail/review-items web/app/api/reviews/route.ts services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailBackfillControllerTest.java web/app/api/gmail/backfills/route.test.ts web/app/api/gmail/review-items/route.test.ts
git commit -m "Expose authenticated Gmail backfill APIs"
```

### Task 7: Build the quiet-background dashboard and Settings controls

**Files:**
- Create: `web/components/gmail-backfill-status.tsx`
- Create: `web/components/gmail-review-queue.tsx`
- Modify: `web/app/page.tsx`
- Modify: `web/app/globals.css`
- Create or modify: `web/app/settings/page.tsx`
- Test: `web/components/gmail-backfill-status.test.tsx`
- Test: `web/components/gmail-review-queue.test.tsx`

**Interfaces:**
- `GmailBackfillStatus` accepts a typed `BackfillRunStatus` and callbacks `onPause`, `onResume`, `onCancel`, `onExtend`.
- `GmailReviewQueue` accepts cursor-paginated review items and emits accept/correct/dismiss commands with optimistic rollback on failure.

- [ ] **Step 1: Write failing component tests**

Test queued, running, first-day-ready, paused, completed-with-warnings, stale, error, and empty states. Assert buttons have accessible names, progress is not color-only, controls are keyboard reachable, and review acceptance removes only the confirmed item.

- [ ] **Step 2: Run frontend tests to verify failure**

Run: `npm --prefix web test -- components/gmail-backfill-status.test.tsx components/gmail-review-queue.test.tsx`.

Expected: FAIL because components do not exist.

- [ ] **Step 3: Implement typed data fetching and polling**

Start a backfill after Gmail connection, poll status with exponential backoff capped at 15 seconds while active, stop polling on terminal states/unmount, and show the first-day-ready link as soon as the priority batch succeeds. Do not render placeholder records.

- [ ] **Step 4: Implement the quiet dashboard surface**

Keep Today’s next actions primary. Add a compact expandable import surface with status, counts, last successful sync, pause/resume/cancel, retry, and review-queue links. Replace the old label-required copy and preserve the existing profile/sidebar layout.

- [ ] **Step 5: Implement review queue and Settings**

Show sender, subject, date, confidence, evidence, missing fields, and actions. Add date range, automatic mode, optional label filter, retention, pause/resume, and extend-history controls with explicit confirmation. Use existing design tokens, no Three.js dependency, and reduced-motion-safe transitions.

- [ ] **Step 6: Run frontend tests and checks**

Run: `npm --prefix web test`; `npm --prefix web run typecheck`; `npm --prefix web run build` only after stopping the active dev server to avoid `.next` cache races.

Expected: PASS with no horizontal overflow at 320px and no demo/fallback data.

- [ ] **Step 7: Commit**

```bash
git add web/components web/app/page.tsx web/app/globals.css web/app/settings
git commit -m "Add quiet Gmail backfill dashboard experience"
```

### Task 8: Add end-to-end verification, observability, and documentation

**Files:**
- Modify: `README.md`
- Modify: `services/README.md`
- Modify: `.env.example`
- Create: `docs/runbooks/gmail-backfill-local.md`
- Create: `web/e2e/gmail-backfill.spec.ts`
- Create: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailBackfillIntegrationTest.java`

- [ ] **Step 1: Write the real-flow acceptance tests**

The backend integration test connects a Gmail API fake that returns real-shaped metadata, publishes a one-day batch and seven-day batches, processes a duplicate delivery, and asserts persisted messages, candidate evidence, review item, and counters. The browser test uses authenticated local test setup only and verifies connect/status/start/poll/review without fixture records presented as user data.

- [ ] **Step 2: Add metrics and safe structured logs**

Emit batch duration, messages scanned, candidates, review items, retries, DLQ count, Gmail rate-limit count, and stale-data age. Log IDs/status/counts only; add redaction tests that fail if tokens, raw body text, prompts, or resume content enters a log event.

- [ ] **Step 3: Document local operation**

Document LocalStack startup, queue creation, required env vars, Gmail OAuth test-user requirements, backfill start, polling, pause/resume/cancel, DLQ replay, and cleanup. State clearly that the app scans real connected Gmail data and creates no demo records.

- [ ] **Step 4: Run the complete test gate**

Run: `mvn test`; `npm --prefix web test`; `npm --prefix web run typecheck`; stop dev servers and run `npm --prefix web run build`; start services and LocalStack; run the browser acceptance test; inspect the dashboard at 320px and desktop widths.

Expected: all tests pass, real Gmail data appears only after import/review, queue progress is observable, failures are recoverable, and no sensitive content appears in logs.

- [ ] **Step 5: Commit**

```bash
git add README.md services/README.md .env.example docs/runbooks/gmail-backfill-local.md web/e2e/gmail-backfill.spec.ts services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailBackfillIntegrationTest.java
git commit -m "Document and verify Gmail backfill operations"
```

## Self-review checklist

- Spec coverage: ingestion, queue/DLQ, date windows, metadata-first filtering, provider abstraction, evidence/review, API status/control, frontend states, security, retention, and acceptance testing each have an explicit task.
- No placeholder language: all steps name files, interfaces, commands, expected results, and commit boundaries.
- Type consistency: `BackfillStatus`, `BackfillBatchStatus`, `BackfillRequest`, `BackfillRunRecord`, `BackfillRunStatus`, `BackfillWindow`, `GmailBackfillRequestedV1`, `GmailBackfillBatchV1`, `CandidateDecision`, and `ClassificationSuggestionV1` are the shared names used across tasks.
- Existing behavior: incremental history sync and label-scoped advanced mode remain covered by existing tests; automatic mode adds a separate date-query path.
- Safety: no task authorizes automatic email sending, Gmail mutation, placeholder records, raw-body queueing, or tenant IDs supplied by the browser.
