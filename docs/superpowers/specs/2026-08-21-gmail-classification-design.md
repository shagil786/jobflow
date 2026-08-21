# JobFlow Gmail Classification and Next-Action Design

## Status

Approved in brainstorming review on 2026-08-21. This document defines the design; implementation requires a separate approved implementation plan.

## Objective

Turn scoped Gmail evidence into trustworthy, reviewable suggestions without silently changing application truth or sending email.

The core loop is:

```text
scoped Gmail message
  -> normalized evidence
  -> deterministic signals
  -> provider-selected AI extraction
  -> validated suggestion
  -> human review
  -> accepted timeline event
  -> next-action calculation
```

## Non-negotiable principles

- AI suggests; the user confirms.
- Unknown and conflicted are valid outcomes.
- A Gmail message, conversation, application, application state, and next action are separate concepts.
- Every extracted fact must point to evidence from a specific message.
- User-confirmed values outrank inferred values.
- No automatic email sending, application submission, application closure, or offer acceptance.
- Email content is untrusted input and must never be treated as instructions to the system.
- Raw email content is short-lived, encrypted, access-controlled, and never logged.

## Classification model

### Message intent

Each message receives one primary intent and a separate direction:

```text
APPLICATION_CONFIRMATION
RECRUITER_OUTREACH
RECRUITER_REPLY
INTERVIEW_INVITATION
INTERVIEW_RESCHEDULE
INTERVIEW_FEEDBACK
REJECTION
OFFER
WITHDRAWAL
FOLLOW_UP_REQUEST
EMPLOYER_UPDATE
UNRELATED
UNKNOWN
```

Direction is derived from the authenticated Gmail account and message headers:

```text
INBOUND | OUTBOUND | UNKNOWN
```

`recruiter` is a message/conversation intent, not an application status.

### Application state

Application state is derived from accepted, immutable timeline events:

```text
captured -> applied -> outreach -> follow_up -> interview -> offer/rejected/closed
```

The timeline retains every event. A later classifier suggestion cannot overwrite an accepted user decision.

### Next action

The action engine independently calculates:

```text
REVIEW_REPLY
FOLLOW_UP
PREPARE
RESOLVE_MATCH
NO_ACTION_CLEAR
NO_ACTION_WAITING
NO_ACTION_BLOCKED
NO_ACTION_SUPPRESSED
```

## Evidence model

Every suggestion and extracted field must reference immutable evidence metadata:

```ts
interface EvidenceSpan {
  evidenceId: string;
  messageId: string;
  threadId: string;
  source: "subject" | "sender" | "recipient" | "body" | "header" | "attachment";
  startOffset?: number;
  endOffset?: number;
  quotedText?: string;
  normalizedTextHash: string;
  classifierVersion: string;
  confidenceContribution?: number;
  sourceAvailable: boolean;
}

interface ExtractedField<T> {
  value?: T;
  evidence: EvidenceSpan[];
  confidence: number;
  source: "user" | "capture" | "header" | "body" | "ai";
  requiresReview: boolean;
  conflict: boolean;
}
```

Rules:

- `offer`, `rejection`, and `interview` require at least one direct evidence span.
- Sender domain alone cannot establish a company.
- Quoted or forwarded text is marked separately and is not fresh evidence by default.
- Evidence quotes are short and capped; complete bodies are not retained as evidence.
- Accepted evidence remains auditable after raw content expires, with `sourceAvailable=false` when necessary.

## Company, role, date, contact, and linking resolution

Evidence priority:

```text
user confirmation
-> browser capture
-> explicit Gmail headers/metadata
-> exact job URL or requisition ID
-> trusted company-domain evidence
-> structured ATS fields
-> subject/body
-> aliases and history
-> generic AI extraction
```

### Company

- Generic ATS domains (`greenhouse.io`, `lever.co`, `workday.com`, `ashbyhq.com`) are platform evidence, not company identity.
- Free-mail domains never establish company identity.
- Recruiter agencies remain separate from employers.
- Conflicting strong signals require review.
- Unknown company is a supported state.

### Role

- Preserve the original role title and store normalized comparison data separately.
- Exact requisition ID or exact captured job URL is strongest.
- Department, skill, seniority, or recruiter title alone cannot establish a role.
- Multiple roles remain candidates until reviewed.

### Dates

Store separate `capturedAt`, `receivedAt`, `sentAt`, `applicationDate`, `statusChangedAt`, `interviewAt`, and `offerAt` values.

- Application date requires explicit submission evidence or user confirmation.
- Gmail received time is not an application date.
- Conflicting explicit dates require review.
- Store timestamps in UTC and render in the user timezone.

### Contacts

- Use actual `From`, `Reply-To`, `To`, and `Cc` addresses.
- Name without an address is not contactable.
- Never guess an email address.
- Shared inboxes are endpoints, not people.
- Contact role requires explicit evidence and remains application-specific.

### Application linking

Match in this order:

```text
exact application/requisition ID
-> existing confirmed thread link
-> exact job URL
-> confirmed company + role
-> sender/contact + compatible role
-> weaker similarity
-> unlinked conversation
```

Suggested thresholds:

- `>= 0.95`: auto-link only with no conflicts
- `0.75-0.949`: reviewable candidate
- `< 0.75`: remain unlinked
- Any competing application or conflict: mandatory review

Threads and applications remain separate entities. One application may have multiple conversations, and one conversation may discuss multiple roles.

## Provider architecture

The AI provider is behind a stable interface:

```text
ClassifierService
  -> ProviderRouter
      -> BedrockProvider
      -> AzureOpenAIProvider
  -> JSONSchemaValidator
  -> EvidenceValidator
  -> ConfidenceCalibrator
```

Both providers must return the same versioned schema. Each result records provider, model, classifier version, prompt version, schema version, and content hash.

Initial deployment policy:

- AWS deployment uses Bedrock.
- Azure deployment uses Azure OpenAI.
- One configured provider processes a classification at a time.
- Cross-cloud automatic fallback is disabled in v1.
- Provider failure retries are bounded and use the same provider before dead-lettering.

Deterministic rules run before AI for explicit offers, rejections, interviews, application confirmations, outbound direction, automated messages, duplicates, and quoted history. AI handles ambiguity, extraction, and evidence selection; it never directly mutates application state.

The AI result must contain:

```ts
interface ClassificationSuggestion {
  suggestionId: string;
  messageId: string;
  threadId: string;
  intent: string;
  direction: "INBOUND" | "OUTBOUND" | "UNKNOWN";
  companyCandidate?: ExtractedField<string>;
  roleCandidate?: ExtractedField<string>;
  contactCandidate?: ExtractedField<string>;
  applicationDateCandidate?: ExtractedField<string>;
  confidence: number;
  evidence: EvidenceSpan[];
  missingFields: string[];
  contradictions: string[];
  requiresReview: true;
  provider: "bedrock" | "azure-openai";
  model: string;
  classifierVersion: string;
  promptVersion: string;
  schemaVersion: string;
  contentHash: string;
}
```

The backend rejects malformed schemas, unsupported intents, evidence-less fields, unavailable evidence references, and unsupported claims.

## Confidence and review

The numeric model score is not treated as calibrated truth. Confidence combines deterministic signal strength, header consistency, evidence quality, identity resolution, model score, and contradiction penalties.

Initial policy:

- `< 0.60`: unknown or mandatory review
- `0.60-0.849`: suggestion with mandatory review
- `0.85+`: stronger suggestion, still mandatory review in v1
- Any contradiction, offer, sensitive extraction, or missing direct evidence: mandatory review regardless of score

The review UI shows proposed intent, evidence snippets, extracted fields, confidence band, missing fields, conflicts, candidate applications, and actions to accept, edit, link, create, defer, suppress, or mark unrelated.

Review acceptance creates an immutable event containing suggestion ID, reviewer ID, accepted values, changed values, evidence version, timestamp, correlation ID, and idempotency key.

## Next-action rules

Evaluate in this order:

1. `NO_ACTION_BLOCKED` when sync is stale, classification incomplete, or evidence conflicts.
2. Terminal state has no action unless a newer confirmed event exists.
3. `REVIEW_REPLY` for a new inbound human message not yet reviewed.
4. `FOLLOW_UP` for unanswered outbound communication or an overdue promised response.
5. `PREPARE` for an upcoming interview, deadline, document request, or response.
6. `NO_ACTION_WAITING` when a future date exists.
7. `NO_ACTION_CLEAR` when no actionable evidence exists.

Timing defaults:

- Explicit recruiter date: use exact date.
- User outreach without response: suggest three business days.
- Application submission without contact: suggest five business days.
- Interview preparation: one business day before.
- “We will get back to you” without a date: waiting; never invent a deadline.

Every action exposes reason, evidence, due date, confidence/confirmation state, and accept/reschedule/snooze/dismiss/complete controls.

## Retention and privacy

- Scope remains `JobFlow/Track` only.
- Raw sanitized body is encrypted and retained for 14 days by default.
- Evidence spans and accepted metadata remain while the associated application exists.
- Attachments are not analyzed in v1.
- No raw bodies, resumes, prompts, tokens, or sensitive excerpts appear in logs/traces.
- Source deletion or expiry is visible to the user as unavailable evidence.
- Account export and deletion must cascade through raw content, evidence, suggestions, and derived records.

## Failure and safety behavior

The system distinguishes:

```text
PENDING       waiting for processing
UNKNOWN       processed but insufficient evidence
UNRELATED     confidently outside job scope
FAILED        retryable or permanent processing failure
DEAD_LETTER   exhausted retry policy
```

Email content is untrusted. Prompt injection, hidden HTML instructions, malformed MIME, forwarded ambiguity, oversized content, provider outage, expired Gmail history, duplicate events, and conflicting user edits must result in abstention, review, retry, or dead-letter handling—not state mutation.

## Idempotency

- Gmail ingestion: `tenantId + userId + connectionId + gmailMessageId`
- Classification: `messageId + classifierVersion + contentHash`
- Review: `suggestionId + acceptedRevision`
- Timeline projection: `eventId + projectionName`
- Follow-up: `applicationId + conversationId + actionType + triggeringEventId`
- Draft generation: `applicationId + threadId + draftType + acceptedActionId + resumeVersionId`

New classifier versions create new suggestion revisions; they never overwrite prior suggestions or accepted decisions.

## Required test fixtures

The implementation must test application confirmations, recruiter outreach, recruiter replies, interviews, reschedules, feedback, rejections, offers, withdrawals, outbound messages, ATS messages, generic senders, newsletters, forwarded mail, quoted replies, multiple roles, duplicate imports, conflicting companies, unknown fields, stale sync, expired cursors, malformed HTML, prompt injection, provider timeout, invalid provider schema, review edits, merge/split candidates, and action recalculation.

Acceptance requires per-intent precision and false-positive reporting, with especially strict review for offers, rejections, company identity, application links, and automatic dates. No automatic projection is enabled until measured accuracy is approved.

## Implementation boundary

This design does not yet implement the classifier, AI providers, review queue, or action engine. The next artifact is a detailed implementation plan that decomposes the work into evidence storage, normalization, deterministic rules, provider adapters, suggestion APIs, review flows, accepted events, and action calculation.
