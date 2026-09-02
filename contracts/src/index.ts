export type ApplicationStatus =
  | "captured"
  | "applied"
  | "outreach"
  | "follow_up"
  | "interview"
  | "rejected"
  | "offer"
  | "closed"
  | "unknown";

export type DraftType = "follow_up" | "outreach" | "application";
export type GmailScanMode = "FOCUSED" | "BROAD" | "FULL";
export type ReviewReason =
  | "MISSING_CORE_FIELD"
  | "CONFLICTING_EVIDENCE"
  | "UNSUPPORTED_CLAIM"
  | "INVALID_CITATION"
  | "LOW_CONFIDENCE"
  | "OFFER_STATUS"
  | "PROVIDER_FAILURE"
  | "RETRIEVAL_FAILURE";

export interface GmailScanProgress {
  runId: string;
  status: "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED";
  totalWindows: number;
  completedWindows: number;
  metadataSeen: number;
  filteredOut: number;
  candidates: number;
  bodiesFetched: number;
  indexedThreads: number;
  classifiedThreads: number;
  autoPromoted: number;
  needsReview: number;
  failed: number;
}

export interface EvidenceSpan {
  messageId: string;
  text: string;
  source: "subject" | "body" | "sender" | "attachment";
}

export type MessageDirection = "INBOUND" | "OUTBOUND" | "UNKNOWN";

export type MessageIntent =
  | "APPLICATION_CONFIRMATION"
  | "RECRUITER_OUTREACH"
  | "RECRUITER_REPLY"
  | "INTERVIEW_INVITATION"
  | "INTERVIEW_RESCHEDULE"
  | "INTERVIEW_FEEDBACK"
  | "REJECTION"
  | "OFFER"
  | "WITHDRAWAL"
  | "FOLLOW_UP_REQUEST"
  | "EMPLOYER_UPDATE"
  | "UNRELATED"
  | "UNKNOWN";

export interface EvidenceSpanV1 {
  tenantId: string;
  userId: string;
  evidenceId: string;
  messageId: string;
  threadId: string;
  source: "subject" | "sender" | "recipient" | "body" | "header";
  quotedText?: string;
  normalizedTextHash: string;
  sourceAvailable: boolean;
}

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

export interface ClassificationSuggestion {
  status: ApplicationStatus;
  company?: string;
  role?: string;
  applicationDate?: string;
  contact?: string;
  confidence: number;
  evidence: EvidenceSpan[];
  missingFields: string[];
  requiresReview: boolean;
}

export interface ClassificationSuggestionV1 {
  tenantId: string;
  userId: string;
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
  reviewReasons: ReviewReason[];
  requiresReview: boolean;
  classifierVersion: string;
  contentHash: string;
}

export interface DraftRequest {
  applicationId: string;
  threadId?: string;
  resumeVersionId?: string;
  draftType: DraftType;
  userInstructions?: string;
}

export interface DraftResponse {
  draftId: string;
  recipient: string;
  subject: string;
  body: string;
  gmailDraftId?: string;
  sent: false;
}

export type ContextPurpose = "CLASSIFICATION" | "RANKING" | "DRAFT" | "FOLLOW_UP";
export type ContextReviewState = "SUPPORTED" | "NEEDS_REVIEW" | "UNKNOWN";

export interface EvidenceDocument {
  tenantId: string;
  userId: string;
  correlationId: string;
  sourceType: "gmail_message" | "gmail_thread" | "application" | "job_capture" | "resume" | "profile";
  sourceId: string;
  threadId?: string;
  applicationId?: string;
  resumeVersionId?: string;
  occurredAt?: string;
  text: string;
  retentionPolicy?: string;
}

export interface EvidenceChunk {
  chunkId: string;
  documentId: string;
  tenantId: string;
  userId: string;
  chunkIndex: number;
  section?: string;
  text: string;
  contentHash: string;
  citation: string;
  embeddingModel?: string;
}

export interface EmbeddingRecord {
  contentHash: string;
  modelVersion: string;
  dimensions: number;
  createdAt: string;
}

export interface ContextQuery {
  tenantId: string;
  userId: string;
  correlationId: string;
  purpose: ContextPurpose;
  query: string;
  sourceTypes?: string[];
  threadId?: string;
  applicationId?: string;
  resumeVersionId?: string;
  limit?: number;
  maxTokens?: number;
}

export interface RetrievedEvidence {
  chunkId: string;
  sourceType: string;
  sourceId: string;
  threadId?: string;
  section?: string;
  text: string;
  score: number;
  citation: string;
}

export interface ContextBundle {
  query: ContextQuery;
  evidence: RetrievedEvidence[];
  estimatedTokens: number;
  state: ContextReviewState;
  warnings: string[];
}

export interface GroundedGenerationRequest {
  tenantId: string;
  userId: string;
  correlationId: string;
  purpose: ContextPurpose;
  instruction: string;
  context: ContextBundle;
  schema?: Record<string, unknown>;
}

export interface GroundedGenerationResponse {
  content: string;
  citations: string[];
  verification: VerificationResult;
  modelVersion: string;
  sent: false;
}

export interface VerificationResult {
  state: ContextReviewState;
  unsupportedClaims: string[];
  invalidEvidence: string[];
  metadata: Record<string, string>;
}

export interface AIProcessingEvent {
  eventId: string;
  eventType: "AI_CONTEXT_INDEXED.v1" | "AI_CONTEXT_RETRIEVED.v1" | "AI_GENERATION_COMPLETED.v1" | "AI_GENERATION_FAILED.v1";
  tenantId: string;
  userId: string;
  correlationId: string;
  occurredAt: string;
  sourceIds: string[];
  modelVersion?: string;
  indexVersion?: string;
}

export interface JobCaptureRequest {
  url: string;
  title: string;
  company?: string;
  role: string;
  location?: string;
  source: string;
  descriptionPreview?: string;
  capturedAt: string;
}

export interface JobCapturedEvent {
  eventId: string;
  eventType: "JobCaptured.v1";
  tenantId: string;
  userId: string;
  correlationId: string;
  idempotencyKey: string;
  occurredAt: string;
  payload: JobCaptureRequest;
}

export type ContactStatus = "DISCOVERED" | "VERIFIED" | "NEEDS_REVIEW" | "REJECTED" | "EXPIRED";
export type EnrichmentStatus = "QUEUED" | "RUNNING" | "PUBLIC_SEARCH" | "GMAIL_SEARCH" | "PROVIDER_SEARCH" | "VERIFYING" | "READY" | "COMPLETED" | "NO_VERIFIED_CONTACT" | "FAILED" | "PROVIDER_UNAVAILABLE";
export type ContactSourceType = "PUBLIC_SOURCE" | "GMAIL" | "PROVIDER" | "USER_PROVIDED";
export type AllowedContactUse = "OUTREACH_DRAFT" | "REFERENCE_ONLY" | "NOT_ALLOWED";

export interface ContactEvidence {
  sourceType: ContactSourceType;
  sourceUrl?: string;
  messageId?: string;
  threadId?: string;
  excerpt: string;
  observedAt?: string;
}

export interface ContactCandidate {
  contactId: string;
  applicationId: string;
  name?: string;
  role?: string;
  company?: string;
  email?: string;
  profileUrl?: string;
  sourceUrl?: string;
  sourceType: ContactSourceType;
  evidence: ContactEvidence;
  confidence: number;
  status: ContactStatus;
  verificationStatus: "SOURCE_CONFIRMED" | "UNVERIFIED" | "REJECTED";
  provider: string;
  providerVersion: string;
  allowedUse: AllowedContactUse;
  selected: boolean;
  preselected?: boolean;
  createdAt: string;
  expiresAt?: string;
}

export interface ContactDiscoverySource {
  sourceUrl: string;
  content?: string;
  title?: string;
}

export interface GmailContactEvidence {
  messageId: string;
  threadId?: string;
  sender: string;
  subject?: string;
  excerpt?: string;
  observedAt?: string;
}

export interface EnrichmentRequest {
  applicationId: string;
  correlationId?: string;
  publicSources?: ContactDiscoverySource[];
  gmailEvidence?: GmailContactEvidence[];
}

export interface EnrichmentResponse {
  enrichmentId: string;
  applicationId: string;
  status: EnrichmentStatus;
  discoveredContacts: number;
  verifiedContacts: number;
  completedAt?: string;
  currentStage?: EnrichmentStatus;
  retryable?: boolean;
}

export interface JobRecommendation {
  recommendationId: string;
  sourceId: string;
  title: string;
  company?: string;
  jobUrl: string;
  fitScore: number;
  matchingEvidence: string[];
  missingRequirements: string[];
  conflicts: string[];
  sourceFreshness?: string;
  recommendedAction: "CAPTURE" | "APPLY_MANUALLY" | "WARM_OUTREACH" | "COLD_OUTREACH" | "IGNORE";
  citations: string[];
}

export type BackfillMode = "AUTOMATIC" | "LABEL_SCOPED";
export type BackfillRunStatus = "QUEUED" | "RUNNING" | "PAUSING" | "PAUSED" | "CANCELLING" | "CANCELLED" | "COMPLETED" | "FAILED";
export type BackfillBatchStatus = "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED" | "DEAD_LETTERED";

export interface GmailBackfillRequestedV1 {
  eventId: string;
  eventType: "GmailBackfillRequested.v1";
  tenantId: string;
  userId: string;
  correlationId: string;
  idempotencyKey: string;
  occurredAt: string;
  runId: string;
  connectionId: string;
  windowFrom: string;
  windowTo: string;
  mode: BackfillMode;
}

export interface GmailBackfillBatchV1 {
  eventId: string;
  eventType: "GmailBackfillBatch.v1";
  tenantId: string;
  userId: string;
  correlationId: string;
  runId: string;
  batchId: string;
  connectionId: string;
  sequenceNo: number;
  windowFrom: string;
  windowTo: string;
  attemptId: string;
  occurredAt: string;
}
