import { createElement as h, type ReactNode } from "react";

export type GmailBackfillState = "QUEUED" | "RUNNING" | "PAUSED" | "COMPLETED" | "FAILED" | "CANCELLED";
export type GmailBackfillStatusValue = { state: GmailBackfillState; completedBatches: number; totalBatches: number; importedMessages: number; candidateMessages: number; firstWindowReady: boolean; oldestProcessedAt?: string; failureCode?: string };

export function getBackfillProgress(status: Pick<GmailBackfillStatusValue, "completedBatches" | "totalBatches">): number {
  if (status.totalBatches <= 0) return 0;
  return Math.min(100, Math.max(0, Math.round((status.completedBatches / status.totalBatches) * 100)));
}

function remainingWindows(status: GmailBackfillStatusValue) { return Math.max(0, status.totalBatches - status.completedBatches); }
function statusCopy(status: GmailBackfillStatusValue) {
  if (status.state === "COMPLETED") return { title: "Gmail scan complete", description: "Your imported messages are ready to review." };
  if (status.state === "FAILED") return { title: "Gmail scan needs attention", description: "The scan stopped before all windows were processed." };
  if (status.state === "PAUSED") return { title: "Gmail scan paused", description: "Your imported data is safe. Resume the remaining windows when you are ready." };
  if (status.state === "CANCELLED") return { title: "Gmail scan cancelled", description: "Imported data remains available; no new windows will be processed." };
  if (status.firstWindowReady) { const remaining = remainingWindows(status); return { title: "Your newest mail is ready", description: `${remaining} ${remaining === 1 ? "window is" : "windows are"} still scanning in the background.` }; }
  return { title: "Preparing your Gmail scan", description: "JobFlow is starting with your newest messages first." };
}

export function GmailBackfillStatus({ status, className = "" }: { status: GmailBackfillStatusValue | null; className?: string }) {
  if (!status) return h("section", { className: `gmail-backfill-status gmail-backfill-status-empty ${className}`.trim(), "aria-labelledby": "gmail-backfill-heading" }, h("div", { className: "gmail-backfill-mark", "aria-hidden": true }, "↗"), h("div", null, h("span", { className: "section-overline" }, "Background import"), h("h2", { id: "gmail-backfill-heading" }, "No Gmail scan is running"), h("p", null, "Connect Gmail to scan job-related conversations with your permission.")));

  const copy = statusCopy(status); const progress = getBackfillProgress(status); const isActive = status.state === "QUEUED" || status.state === "RUNNING" || status.state === "PAUSED";
  const metrics: ReactNode[] = [h("span", { key: "messages" }, h("strong", null, status.importedMessages), " messages found"), h("span", { key: "candidates" }, h("strong", null, status.candidateMessages), " need review")];
  if (status.firstWindowReady) metrics.push(h("span", { key: "window" }, h("strong", null, "1 day"), " available now"));
  return h("section", { className: `gmail-backfill-status ${isActive ? "is-active" : ""} ${className}`.trim(), "aria-labelledby": "gmail-backfill-heading" },
    h("div", { className: "gmail-backfill-top" }, h("div", { className: "gmail-backfill-heading" }, h("span", { className: `gmail-backfill-mark ${status.state.toLowerCase()}`, "aria-hidden": true }, status.state === "COMPLETED" ? "✓" : status.state === "FAILED" ? "!" : "↗"), h("div", null, h("span", { className: "section-overline" }, "Background import"), h("h2", { id: "gmail-backfill-heading" }, copy.title))), h("span", { className: "gmail-backfill-state" }, status.state.toLowerCase())),
    h("p", { className: "gmail-backfill-description" }, copy.description),
    isActive && h("div", { className: "gmail-backfill-progress", "aria-label": `Gmail scan progress: ${progress}%`, role: "progressbar", "aria-valuemin": 0, "aria-valuemax": 100, "aria-valuenow": progress }, h("span", { style: { width: `${progress}%` } })),
    h("div", { className: "gmail-backfill-metrics" }, metrics),
    status.state === "FAILED" && status.failureCode ? h("p", { className: "gmail-backfill-error", role: "alert" }, "We could not finish this scan. Try reconnecting Gmail and starting again.") : null,
  );
}
