import { describe, expectTypeOf, it } from "vitest";
import type {
  ClassificationSuggestion,
  ClassificationSuggestionV1,
  EvidenceSpanV1,
  ExtractedFieldCandidate,
  MessageDirection,
  MessageIntent,
  SafeGmailMessageV1,
  ReviewReason,
} from "../../contracts/src/index";

describe("versioned evidence and identity contracts", () => {
  it("exports the expected message direction and intent unions", () => {
    expectTypeOf<MessageDirection>().toEqualTypeOf<"INBOUND" | "OUTBOUND" | "UNKNOWN">();
    expectTypeOf<MessageIntent>().toEqualTypeOf<
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
      | "UNKNOWN"
    >();
  });

  it("exports the expected evidence span contract", () => {
    expectTypeOf<EvidenceSpanV1>().toEqualTypeOf<{
      tenantId: string;
      userId: string;
      evidenceId: string;
      messageId: string;
      threadId: string;
      source: "subject" | "sender" | "recipient" | "body" | "header";
      quotedText?: string;
      normalizedTextHash: string;
      sourceAvailable: boolean;
    }>();
  });

  it("exports the expected candidate, safe message, and suggestion contracts", () => {
    expectTypeOf<ExtractedFieldCandidate<string>>().toEqualTypeOf<{
      value?: string;
      confidence: number;
      evidence: EvidenceSpanV1[];
      source: "capture" | "header" | "body" | "ai" | "user";
      requiresReview: boolean;
      conflict: boolean;
    }>();

    expectTypeOf<SafeGmailMessageV1>().toEqualTypeOf<{
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
    }>();

    expectTypeOf<ClassificationSuggestionV1>().toEqualTypeOf<{
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
    }>();
  });

  it("keeps the legacy classification suggestion contract available", () => {
    expectTypeOf<ClassificationSuggestion>().toMatchTypeOf<{
      confidence: number;
      evidence: { messageId: string; text: string; source: "subject" | "body" | "sender" | "attachment" }[];
      missingFields: string[];
      requiresReview: boolean;
    }>();
  });
});
