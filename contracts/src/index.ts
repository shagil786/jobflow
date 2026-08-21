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
