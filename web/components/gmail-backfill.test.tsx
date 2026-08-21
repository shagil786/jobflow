import { renderToStaticMarkup } from "react-dom/server";
import { createElement } from "react";
import { describe, expect, it } from "vitest";
import { GmailBackfillStatus, getBackfillProgress } from "./gmail-backfill";

describe("GmailBackfillStatus", () => {
  it("shows the first-day result while older windows continue in the background", () => {
    const html = renderToStaticMarkup(
      createElement(GmailBackfillStatus, {
        status: {
          state: "RUNNING",
          completedBatches: 1,
          totalBatches: 9,
          importedMessages: 14,
          candidateMessages: 5,
          firstWindowReady: true,
          oldestProcessedAt: "2026-08-20T00:00:00Z",
        },
      }),
    );

    expect(html).toContain("Your newest mail is ready");
    expect(html).toContain(">14</strong> messages found");
    expect(html).toContain("8 windows are still scanning");
    expect(html).toContain('aria-valuenow="11"');
  });

  it("does not render a fake progress bar when no run exists", () => {
    const html = renderToStaticMarkup(createElement(GmailBackfillStatus, { status: null }));

    expect(html).toContain("No Gmail scan is running");
    expect(html).not.toContain("aria-valuenow");
  });

  it("calculates bounded progress for empty and complete runs", () => {
    expect(getBackfillProgress({ completedBatches: 0, totalBatches: 0 })).toBe(0);
    expect(getBackfillProgress({ completedBatches: 3, totalBatches: 2 })).toBe(100);
    expect(getBackfillProgress({ completedBatches: 1, totalBatches: 4 })).toBe(25);
  });
});
