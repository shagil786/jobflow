import { createElement as h, useState, type ChangeEvent } from "react";
import { formatGmailMessageBody } from "../lib/gmail-message-display";

export type GmailReviewItem = { connectionId: string; suggestion: { suggestionId: string; messageId: string; threadId: string; intent: string; direction?: "INBOUND" | "OUTBOUND" | "UNKNOWN"; confidence: number; company?: { value?: string }; role?: { value?: string }; applicationDate?: { value?: string }; contact?: { value?: string }; evidence?: Array<{ source?: string; quotedText?: string; messageId?: string }>; missingFields: string[]; reviewReasons?: string[] } };
type LiveThread = { threadId: string; messages: Array<{ messageId: string; direction: "INBOUND" | "OUTBOUND"; sender?: string; recipients: string[]; subject?: string; receivedAt?: string; body?: string }> };
export type GmailReviewDecision = { decision: "ACCEPT" | "CORRECT" | "DISMISS"; company?: string; role?: string };

export function GmailReviewQueue({ items, savingId, onDecision, hasMore, loadingMore, onLoadMore }: { items: GmailReviewItem[]; savingId: string | null; onDecision: (id: string, payload: GmailReviewDecision) => void; hasMore: boolean; loadingMore: boolean; onLoadMore: () => void }) {
  const loadWhenNearBottom = (event: { currentTarget: HTMLDivElement }) => {
    const element = event.currentTarget;
    if (element.scrollHeight - element.scrollTop - element.clientHeight < 180) onLoadMore();
  };
  return h("section", { className: "section-card review-card gmail-review-queue", "aria-labelledby": "review-heading" }, h("div", { className: "section-head" }, h("div", null, h("span", { className: "section-overline" }, "Evidence inbox"), h("h2", { id: "review-heading", className: "section-title" }, "Needs your review"), h("p", { className: "section-caption" }, "Suggestions stay out of your timeline until you confirm them.")), h("span", { className: "count-pill" }, items.length, hasMore ? "+ items" : items.length === 1 ? " item" : " items")), items.length ? h("div", { className: "review-scroll", onScroll: loadWhenNearBottom }, h("div", { className: "review-list" }, items.map((item) => h(GmailReviewCard, { key: item.suggestion.suggestionId, item, saving: savingId === item.suggestion.suggestionId, onDecision })), h("div", { className: "review-pagination", "aria-live": "polite" }, hasMore ? h("button", { className: "outline-button", type: "button", onClick: onLoadMore, disabled: loadingMore }, loadingMore ? "Loading more…" : "Load more reviews") : h("span", null, "You have reached the end of the review queue.")))) : h("div", { className: "empty-state compact-empty" }, h("div", { className: "empty-icon soft", "aria-hidden": true }, "✦"), h("strong", null, "No evidence waiting"), h("span", null, "When JobFlow finds a job-related message, it will appear here before it becomes a record.")));
}

function GmailReviewCard({ item, saving, onDecision }: { item: GmailReviewItem; saving: boolean; onDecision: (id: string, payload: GmailReviewDecision) => void }) {
  const suggestion = item.suggestion;
  const [editing, setEditing] = useState(false);
  const [company, setCompany] = useState(suggestion.company?.value ?? "");
  const [role, setRole] = useState(suggestion.role?.value ?? "");
  const [thread, setThread] = useState<LiveThread | null>(null);
  const [threadLoading, setThreadLoading] = useState(false);
  const [threadError, setThreadError] = useState("");
  const confidence = Math.round(Math.min(1, Math.max(0, suggestion.confidence)) * 100);
  const intent = suggestion.intent.replaceAll("_", " ").toLowerCase();
  const direction = suggestion.direction === "OUTBOUND"
    ? { label: "Sent", icon: "↗", className: "outbound", description: "Message sent by you" }
    : suggestion.direction === "INBOUND"
      ? { label: "Received", icon: "↙", className: "inbound", description: "Message received from a recruiter or company" }
      : { label: "Direction unknown", icon: "↔", className: "unknown", description: "The message direction could not be verified" };
  const evidence = suggestion.evidence ?? [];
  const sourceMessageId = suggestion.messageId || evidence.find(span => span.messageId)?.messageId || "";
  const updateCompany = (event: ChangeEvent<HTMLInputElement>) => setCompany(event.target.value);
  const updateRole = (event: ChangeEvent<HTMLInputElement>) => setRole(event.target.value);
  const loadThread = async () => {
    setThreadLoading(true); setThreadError("");
    try {
      if (!sourceMessageId) throw new Error("This review has no retrievable Gmail message");
      const response = await fetch(`/api/gmail/messages/${encodeURIComponent(sourceMessageId)}/thread`, { cache: "no-store" });
      if (!response.ok) { const error = await response.json().catch(() => null) as { error?: { message?: string }; code?: string; message?: string } | null; throw new Error(error?.error?.message ?? error?.message ?? `Thread unavailable (${response.status})`); }
      const body = await response.json() as { thread?: LiveThread };
      if (!body.thread) throw new Error("Thread unavailable");
      setThread(body.thread);
    } catch (error) { setThreadError(error instanceof Error ? error.message : "The live thread could not be loaded. Nothing was changed."); }
    finally { setThreadLoading(false); }
  };
  const actions = editing
    ? h("button", { className: "primary-button", type: "button", disabled: saving, onClick: () => onDecision(suggestion.suggestionId, { decision: "CORRECT", company, role }) }, saving ? "Saving…" : "Save correction")
    : h("div", { className: "gmail-review-buttons" },
      h("button", { className: "outline-button", type: "button", disabled: saving, onClick: () => onDecision(suggestion.suggestionId, { decision: "DISMISS" }) }, "Dismiss"),
      h("button", { className: "outline-button", type: "button", disabled: saving, onClick: () => setEditing(true) }, "Correct"),
      h("button", { className: "primary-button", type: "button", disabled: saving, onClick: () => onDecision(suggestion.suggestionId, { decision: "ACCEPT" }) }, saving ? "Saving…" : "Confirm"));
  const liveThreadView = thread && h("div", { className: "live-thread" },
    h("div", { className: "thread-detail-label" }, "Live Gmail thread · raw content is not persisted by JobFlow"),
    thread.messages.map(message => h("div", { className: `live-message ${message.direction === "OUTBOUND" ? "sent" : "received"}`, key: message.messageId },
      h("div", { className: "live-message-head" },
        h("span", { className: `message-direction ${message.direction === "OUTBOUND" ? "outbound" : "inbound"}` }, message.direction === "OUTBOUND" ? "↗ Sent" : "↙ Received"),
        h("time", null, message.receivedAt ? new Date(message.receivedAt).toLocaleString() : "Time unavailable")),
      h("strong", null, message.subject ?? "(no subject)"),
      h("p", null, formatGmailMessageBody(message.body))
    )));
  const detail = h("details", { className: "thread-detail" },
    h("summary", null, "View evidence", h("span", { "aria-hidden": true }, "↗")),
    h("div", { className: "thread-detail-body" },
      h("div", { className: "thread-detail-label" }, direction.description, " · ", evidence.length, evidence.length === 1 ? " citation" : " citations"),
      evidence.length ? h("div", { className: "citation-list" }, evidence.map((span, index) => h("blockquote", { className: "citation", key: `${span.messageId ?? "evidence"}-${index}` }, h("span", { className: "citation-source" }, span.source ?? "Gmail message"), span.quotedText ?? "Citation verified; message text is protected and not retained in this view."))) : h("p", { className: "thread-detail-empty" }, "No citation was retained for this suggestion. It remains reviewable, but no unsupported detail is shown."),
      h("button", { className: "outline-button thread-load-button", type: "button", onClick: loadThread, disabled: threadLoading }, threadLoading ? "Loading live thread…" : thread ? "Refresh live thread" : "Open live thread"),
      threadError && h("p", { className: "thread-detail-error", role: "alert" }, threadError), liveThreadView));
  return h("article", { className: "gmail-review-item" },
    h("div", { className: "gmail-review-item-main" },
      h("div", { className: "review-top" }, h("strong", { className: "review-name" }, suggestion.company?.value ?? "Unknown company"), h("span", { className: `message-direction ${direction.className}`, title: direction.description }, h("span", { "aria-hidden": true }, direction.icon), " ", direction.label)),
      h("div", { className: "review-meta" }, h("span", { className: "review-role" }, suggestion.role?.value ?? "Role needs confirmation", " ", h("span", { "aria-hidden": true }, "·"), " ", intent), h("span", { className: "confidence" }, confidence, "% confidence")),
      suggestion.contact?.value && h("div", { className: "gmail-review-detail" }, "Contact: ", suggestion.contact.value),
      suggestion.applicationDate?.value && h("div", { className: "gmail-review-detail" }, "Date: ", suggestion.applicationDate.value),
      (suggestion.reviewReasons?.length || suggestion.missingFields.length) && h("div", { className: "review-reasons", "aria-label": "Why this needs review" }, (suggestion.reviewReasons ?? []).map(reason => h("span", { className: "reason-pill", key: reason }, reason.replaceAll("_", " ").toLowerCase())), suggestion.missingFields.map(field => h("span", { className: "reason-pill", key: `missing-${field}` }, `missing ${field}`))),
      h("div", { className: "evidence" }, "JobFlow found a ", intent, " signal in your connected Gmail. ", suggestion.missingFields.length ? `Still needed: ${suggestion.missingFields.join(", ")}.` : "The suggested fields are complete."),
      detail,
      editing && h("div", { className: "review-edit" }, h("label", null, "Company", h("input", { value: company, onChange: updateCompany })), h("label", null, "Role", h("input", { value: role, onChange: updateRole }))),
    ),
    h("div", { className: "review-actions" }, actions));
}
