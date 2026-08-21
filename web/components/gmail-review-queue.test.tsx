import { renderToStaticMarkup } from "react-dom/server";
import { createElement } from "react";
import { describe, expect, it } from "vitest";
import { GmailReviewQueue, type GmailReviewItem } from "./gmail-review-queue";

const item: GmailReviewItem = {
  connectionId: "connection-1",
  suggestion: {
    suggestionId: "suggestion-1",
    messageId: "message-1",
    threadId: "thread-1",
    intent: "INTERVIEW_INVITATION",
    confidence: 0.86,
    company: { value: "Acme" },
    role: { value: "Frontend Engineer" },
    missingFields: [],
  },
};

describe("GmailReviewQueue", () => {
  it("renders grounded evidence and review controls without inventing records", () => {
    const html = renderToStaticMarkup(createElement(GmailReviewQueue, { items: [item], savingId: null, onDecision: () => undefined }));

    expect(html).toContain("Needs your review");
    expect(html).toContain("Acme");
    expect(html).toContain("Frontend Engineer");
    expect(html).toContain("86% confidence");
    expect(html).toContain("Confirm");
    expect(html).toContain("Dismiss");
    expect(html).toContain("Correct");
  });

  it("renders a truthful empty state", () => {
    const html = renderToStaticMarkup(createElement(GmailReviewQueue, { items: [], savingId: null, onDecision: () => undefined }));

    expect(html).toContain("No evidence waiting");
    expect(html).not.toContain("Acme");
  });

  it("does not expose message identifiers as user-facing evidence", () => {
    const html = renderToStaticMarkup(createElement(GmailReviewQueue, { items: [item], savingId: null, onDecision: () => undefined }));

    expect(html).not.toContain("message-1");
    expect(html).not.toContain("thread-1");
  });
});
