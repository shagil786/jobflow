# Task 5 Report

Date: 2026-08-21
Worktree: `/Users/mdshagilnizami/Documents/jobs/jobflow/.worktrees/evidence-identity-foundation`

## Summary

Task 5 final hardening fixes are complete. Flyway V6 replaces the owner-only Gmail connection uniqueness with normalized `(tenant_id, user_id, email)` identity, and V7 adds a persisted active-mailbox marker. Connect transactionally activates the newly connected mailbox, status reads that marker, and equal `connectedAt` values cannot make status select an arbitrary row. All Gmail calls used by sync map upstream, auth, network, malformed-response, and fetch failures to typed safe errors.

## Verification Results

- Tenant and authorization boundaries:
  - Wrong internal key is covered by `GmailConnectionControllerTest` and returns `401`.
  - Missing internal key is covered by `GmailConnectionControllerTest` and returns a controlled `401` envelope.
  - Unknown connections on the internal sync and cursor routes are covered by `GmailConnectionControllerTest` and return `404` with `{"code":"GMAIL_CONNECTION_NOT_FOUND","message":"Gmail connection not found"}`; neither response includes owner/tenant data.
  - Cross-tenant message lookup remains non-returning and is already covered by `GmailMessageStoreTest`.
  - Same-mailbox reconnect identity reuse and different-mailbox identity separation are covered by `GmailConnectionServiceTest`.
  - `GmailConnectionPersistenceTest` applies all seven Flyway migrations and persists two same-owner mailboxes with distinct IDs while preserving same-mailbox idempotency.
  - `GmailConnectionServiceTest` proves equal timestamps select the newly connected mailbox deterministically; reconnecting another mailbox makes that mailbox active.
  - `GmailConnectionPersistenceTest` applies the legacy schema through V7, verifies the owner-only index is gone, verifies the normalized mailbox index exists, allows the same normalized email for different owners, and rejects a duplicate same-owner/mailbox row at the database boundary.
  - First-insert owner mismatches are covered by `GmailMessageStoreTest` and `GmailThreadStoreTest`; both reject before persistence.

- Controlled errors:
  - Bad internal authentication returns `INTERNAL_AUTHENTICATION_FAILED` without the supplied key.
  - Gmail fetch failures return `GMAIL_FETCH_FAILED` with `502 Bad Gateway`, without tokens or Gmail response bodies; request IDs are returned when supplied.
  - `RestGmailApiClientTest` covers refresh-token, profile/history, label, message-list, history-list, metadata/body, network/non-success, and bounded-content failure paths; assertions verify the raw upstream response text is absent from the typed error message.

- Retention and logging:
  - Source scan found no ingestion-service application logger calls that emit raw body content, prompts, or OAuth tokens.
  - `RestGmailApiClient` continues to fetch message metadata separately from bounded body-processing content, rejects decoded content above `256 KiB`, and ignores attachment parts.
  - `gmail_messages` persistence continues to store only metadata plus `normalizedContentHash`; no raw Gmail body content is persisted in the current implementation.
  - `docs/jobflow-design.md` now links the evidence plan and records the 14-day configurable raw-content default plus the v1 attachment-analysis exclusion.

## Checks Run

- Backend:
  - `mvn -q -Dmaven.repo.local=/private/tmp/jobflow-review-m2 -Dtest=GmailConnectionPersistenceTest,GmailConnectionServiceTest test` — passed.
  - `mvn -q -Dmaven.repo.local=/private/tmp/jobflow-review-m2 -Dtest=GmailConnectionPersistenceTest,GmailConnectionServiceTest,RestGmailApiClientTest,GmailConnectionControllerTest test` — passed.
  - `cd services/ingestion-service && mvn -q -Dmaven.repo.local=/private/tmp/jobflow-review-m2 test` — passed.

- Web:
  - `cd web && npm test -- --run && npm run typecheck` — passed: 13 test files, 28 tests, and typecheck passed.

All required final hardening checks passed on 2026-08-21.

## Operational Smoke Checks

- `curl http://localhost:8082/actuator/health`
- `curl -X POST -H 'X-Internal-Service-Key: wrong' http://localhost:8082/internal/v1/gmail/connections/00000000-0000-0000-0000-000000000000/sync`

These could not complete because no local service was listening on `localhost:8082` at verification time:

- health probe result: `curl: (7) Failed to connect to localhost port 8082`
- invalid-key probe result: `curl: (7) Failed to connect to localhost port 8082`

No OAuth or Gmail state was changed during Task 5.
