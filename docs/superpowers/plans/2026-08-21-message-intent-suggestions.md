# Message-intent suggestions and persistence plan

## Goal

Turn each safe, normalized Gmail message into an auditable deterministic intent suggestion. Suggestions are versioned, tenant-owned, evidence-backed, review-required, and never mutate an application or send email.

## Scope

In scope:

- deterministic rules for the approved `MessageIntent` values;
- confidence, evidence, contradiction, and unknown handling;
- versioned persistence keyed by tenant, user, connection, and Gmail message identity;
- idempotent reprocessing by message content hash and classifier version;
- focused backend and contract tests.

Out of scope:

- Bedrock/Azure provider calls;
- automatic application status updates;
- review acceptance UI/workflow;
- next-action calculation;
- email sending or automatic replies.

## Decisions

1. Rules suggest; users confirm later. Every persisted suggestion has `requiresReview=true`.
2. Unknown and conflicted are first-class intents. Low-confidence matches remain unknown.
3. Evidence spans must reference the message/thread and normalized content hash. No raw body is persisted or logged.
4. The classifier consumes `SafeGmailMessage` plus bounded normalized text/evidence prepared by the existing evidence service.
5. One current suggestion is stored per `(tenantId, userId, connectionId, messageId, classifierVersion, contentHash)`. Reprocessing a changed message creates a new versioned record; it never overwrites prior evidence.
6. Tenant/user/connection ownership is validated before persistence.

## Planned interfaces

```java
interface MessageIntentClassifier {
  ClassificationSuggestion classify(SafeGmailMessage message, PreparedEvidence evidence);
}

interface ClassificationSuggestionStore {
  ClassificationSuggestionRecord saveIfAbsent(ClassificationSuggestion suggestion);
  Optional<ClassificationSuggestionRecord> findLatestByProviderIdentity(
      String tenantId, String userId, UUID connectionId, String messageId);
}
```

The Java model mirrors the existing `ClassificationSuggestionV1` contract and includes suggestion ID, classifier version, content hash, intent, direction, confidence, candidate fields, evidence spans, missing fields, contradictions, and `requiresReview=true`.

## Rule policy

- Strong subject/body phrases can produce a non-unknown intent only when sender/direction/evidence are compatible.
- Contradictory strong signals produce `UNKNOWN` with `conflict=true` and both evidence references.
- Generic ATS senders do not establish a company.
- A received timestamp never establishes application date.
- Confidence is bounded to `[0,1]`; no candidate without evidence.
- Rules are pure and deterministic; classifier version is explicit (for example `rules-2026-08-21-v1`).

## Test gates

- exact phrase/rule matrix for every intent;
- unknown, low-confidence, and contradictory messages;
- inbound/outbound/unknown direction;
- evidence/hash/provenance preservation;
- same message idempotency and changed-content versioning;
- cross-tenant and wrong-connection rejection;
- Flyway migration/data preservation;
- full ingestion Maven suite and web contract/typecheck suite.
