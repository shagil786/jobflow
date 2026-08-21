# Suggestion Persistence Task Report

Date: 2026-08-21
Worktree: `/Users/mdshagilnizami/Documents/jobs/jobflow/.worktrees/suggestion-persistence`

## Scope Completed

- Added immutable `ClassificationSuggestionV1` persistence for tenant/user/connection/message scoped Gmail suggestions.
- Added tenant-safe repository/store/service boundary with idempotent `saveIfAbsent` and scoped latest lookup.
- Added Flyway `V9__create_classification_suggestions.sql` and follow-up `V10__order_classification_suggestions_by_row_id.sql`.
- Derived persisted `suggestion_id` from tenant, user, connection, message, classifier-version, and content-hash scope while retaining same-scope idempotency.
- Changed latest lookup to use database identity `row_id` insertion order; `created_at` remains informational.
- Added atomic concurrent-save recovery: the insert/flush runs in a separate transaction, identity constraint races roll back and clear JPA state, then reload the exact scoped row; unrelated integrity violations propagate.
- Sanitized persisted evidence by dropping `quotedText` before rows are written.
- Added focused store/service/migration tests for idempotency, changed-content/versioned inserts, cross-owner same-message persistence, insertion-order latest lookup despite out-of-order timestamps, owner mismatch rejection, tenant-scoped lookup, and additive migration behavior.

## Verification

1. Focused persistence and migration tests

   Command:
   ```bash
   mvn -q -Dtest=ClassificationSuggestionStoreTest,ClassificationSuggestionServiceTest,FlywayMigrationTest test
   ```

   Result: passed
   - `ClassificationSuggestionStoreTest`: 6 tests, 0 failures, 0 errors
   - `ClassificationSuggestionServiceTest`: 1 test, 0 failures, 0 errors
   - `FlywayMigrationTest`: 4 tests, 0 failures, 0 errors
   - Total: 11 tests, 0 failures, 0 errors, 0 skipped

2. Full ingestion Maven suite

   Command:
   ```bash
   mvn -q -Dmaven.repo.local=/private/tmp/jobflow-review-m2 test
   ```

   Result: passed
   - Total from Surefire reports: 68 tests, 0 failures, 0 errors, 0 skipped

3. Explicit classifier verification

   Command:
   ```bash
   mvn -q -Dmaven.repo.local=/private/tmp/jobflow-review-m2 -Dtest=DeterministicMessageIntentClassifierTest test
   ```

   Result: passed
   - `DeterministicMessageIntentClassifierTest`: 5 tests, 0 failures, 0 errors, 0 skipped

## Notes

- The red TDD phase first failed on missing `ClassificationSuggestionStore`/repository/service types, then on a JPA/Flyway column-type mismatch caused by `@Lob`; the final mapping removes `@Lob` so Hibernate validates the Flyway `text` columns cleanly.
- The concurrency red phase reproduced a uniqueness exception from two simultaneous same-scope saves; the green implementation uses a `REQUIRES_NEW` write transaction with `saveAndFlush`, rollback, `EntityManager.clear()`, and an exact scoped-key reload.
- Test output still shows existing non-failing warnings about Flyway’s tested H2 version range and Mockito self-attachment on the current JDK.
