# Classifier Slice Report

Date: 2026-08-21

## Scope implemented

- Java-side versioned classifier contracts for the message-intent suggestion model.
- Pure deterministic `MessageIntentClassifier` implementation consuming `SafeGmailMessage` and `GmailEvidenceService.PreparedEvidence`.
- Deterministic handling for all approved intents plus `UNKNOWN`, contradiction handling, low-confidence fallback, direction derivation, evidence provenance, content hash preservation, confidence clamping, and `requiresReview=true`.

## Explicitly not implemented

- Persistence, repositories, entities, migrations, Flyway changes, controllers, AI/provider code, UI, email sending, or application mutation.

## Files added

- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/MessageIntent.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/MessageDirection.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/EvidenceSpanV1.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/ExtractedFieldCandidateV1.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/ClassificationSuggestionV1.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/ClassificationSuggestionRecord.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/MessageIntentClassifier.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/DeterministicMessageIntentClassifier.java`

## Verification

- `cd services/ingestion-service && mvn -q -DskipTests compile`
  - Passed.
- `DeterministicMessageIntentClassifierTest`
  - Compiled in isolation against `target/classes`.
  - Executed via a direct JUnit Platform launcher run.
  - Result: `testsFound=5`, `testsSucceeded=5`, `testsFailed=0`.

## Verification limits / blockers

- A normal Maven test run in this worktree is currently blocked by a pre-existing draft persistence test: `services/ingestion-service/src/test/java/dev/jobflow/ingestion/ClassificationSuggestionStoreTest.java`. That file references persistence classes which are intentionally out of scope for this classifier-only task.
- `cd contracts && npm run typecheck` could not run because `tsc` is not installed in the current environment.
- `cd web && npm test -- --run lib/contracts.test.ts` and `cd web && npm run typecheck` could not run because `vitest` / `tsc` are not installed in the current environment.

## Scope check

- No persistence implementation was added for this task.
- Existing draft persistence-related changes in this worktree were left untouched.
