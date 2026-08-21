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
  requiresReview: true;
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
