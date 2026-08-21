# Task 4 Report

Date: 2026-08-21
Worktree: `/Users/mdshagilnizami/Documents/jobs/jobflow/.worktrees/evidence-identity-foundation`

## Scope completed

Implemented deterministic Gmail evidence preparation only:

- `EmailNormalizer` for HTML/text cleanup, quoted-content separation, bounded-content enforcement, and SHA-256 hashing.
- `IdentityCandidateExtractor` for deterministic company, role, application-date, and contact candidates with short evidence spans and bounded confidence.
- `GmailEvidenceService` for tenant-owned evidence preparation using existing connection ownership and Gmail body fetches, always returning review-required `UNKNOWN` intent without AI or application mutation.

No AI provider, review acceptance flow, application mutation, attachment analysis, or UI work was added.

## Files changed

- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/EmailNormalizer.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/IdentityCandidateExtractor.java`
- `services/ingestion-service/src/main/java/dev/jobflow/ingestion/GmailEvidenceService.java`
- `services/ingestion-service/src/test/java/dev/jobflow/ingestion/EmailNormalizerTest.java`
- `services/ingestion-service/src/test/java/dev/jobflow/ingestion/IdentityCandidateExtractorTest.java`
- `services/ingestion-service/src/test/java/dev/jobflow/ingestion/GmailEvidenceServiceTest.java`

## TDD evidence

Red:

- Ran `mvn -q -Dmaven.repo.local=/private/tmp/jobflow-review-m2 -Dtest=EmailNormalizerTest,IdentityCandidateExtractorTest,GmailEvidenceServiceTest test`
- Result: expected compilation failures because `EmailNormalizer`, `IdentityCandidateExtractor`, and `GmailEvidenceService` did not exist yet.

Green:

- Re-ran `mvn -q -Dmaven.repo.local=/private/tmp/jobflow-review-m2 -Dtest=EmailNormalizerTest,IdentityCandidateExtractorTest,GmailEvidenceServiceTest test`
- Result: pass, `12` tests green across the three new Task 4 suites.

Full ingestion suite:

- Ran `mvn -q -Dmaven.repo.local=/private/tmp/jobflow-review-m2 test`
- Result: pass, `33` tests green across `10` suites:
  - `GmailConnectionServiceTest`: 2
  - `EmailNormalizerTest`: 4
  - `GmailMessageStoreTest`: 4
  - `GmailEvidenceServiceTest`: 2
  - `IdentityCandidateExtractorTest`: 6
  - `GmailSyncServiceTest`: 3
  - `GmailThreadStoreTest`: 2
  - `FlywayMigrationTest`: 2
  - `GmailSyncScopeTest`: 3
  - `RestGmailApiClientTest`: 5

## Assumptions

- When the body says `application submitted on March 4` without a year, the extracted application-date candidate preserves the exact phrase value `March 4` rather than inferring a year.
- A non-generic employer domain in sender headers can support a company candidate, but header-only company evidence always remains review-required and never auto-confirms identity.
- Evidence preparation in Task 4 stops at a safe `UNKNOWN` intent bundle; intent classification remains intentionally deferred.

## Residual limitations

- Role extraction is intentionally conservative and only captures straightforward subject/body role phrasing.
- Quoted and forwarded history detection is deterministic and based on common textual markers, not full MIME thread reconstruction.
- Evidence spans are short text snippets with `sourceAvailable=true`; raw bodies are not persisted or logged by this implementation.
