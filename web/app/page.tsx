"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { GmailReviewQueue, type GmailReviewDecision, type GmailReviewItem } from "../components/gmail-review-queue";
import { CaptureWorkflow } from "../components/capture-workflow";
import { formatGmailMessageBody } from "../lib/gmail-message-display";

type Action = {
  id: string;
  title: string;
  company: string;
  role: string;
  date: string;
  kind: "Follow up" | "Review reply" | "Prepare" | "Rejected";
  tone: "amber" | "blue" | "green";
  direction?: "INBOUND" | "OUTBOUND";
  sourceMessageId?: string;
};

type ApiApplication = {
  id: string;
  company: string;
  role: string;
  jobUrl: string;
  source: string;
  location?: string;
  status: string;
  updatedAt: string;
  sourceDirection?: "INBOUND" | "OUTBOUND";
  sourceMessageId?: string;
};
type LiveThread = { threadId: string; messages: Array<{ messageId: string; direction: "INBOUND" | "OUTBOUND"; sender?: string; recipients: string[]; subject?: string; receivedAt?: string; body?: string }> };

type ReviewSuggestion = GmailReviewItem;
type ReviewPayload = GmailReviewDecision;
type ReviewPage = { items: ReviewSuggestion[]; nextCursor: string | null; rejectedCompanies?: string[] };
type GmailScanMode = "FOCUSED" | "BROAD" | "FULL";
type GmailScanProgress = { status?: string; totalBatches?: number; completedBatches?: number; importedMessages?: number; metadataSeen?: number; filteredOut?: number; candidates?: number; bodiesFetched?: number; indexedThreads?: number; classifiedThreads?: number; autoPromoted?: number; needsReview?: number; failedBatches?: number };
type Recommendation = { recommendationId: string; title: string; company?: string; jobUrl: string; fitScore: number; missingRequirements: string[]; recommendedAction: string; citations: string[] };
const REVIEW_PAGE_SIZE = 25;

export default function Home() {
  const [actions, setActions] = useState<Action[]>([]);
  const [rejectedActions, setRejectedActions] = useState<Action[]>([]);
  const [filter, setFilter] = useState("All");
  const [toast, setToast] = useState("");
  const toastTimer = useRef<number | null>(null);
  const [loadState, setLoadState] = useState<"loading" | "ready" | "auth" | "error">("loading");
  const [captureOpen, setCaptureOpen] = useState(false);
  const [signOutSaving, setSignOutSaving] = useState(false);
  const [gmailConnected, setGmailConnected] = useState(false);
  const [gmailEmail, setGmailEmail] = useState("");
  const [gmailSyncing, setGmailSyncing] = useState(false);
  const [gmailSyncMessage, setGmailSyncMessage] = useState("");
  const [gmailScanMode, setGmailScanMode] = useState<GmailScanMode>("FOCUSED");
  const [gmailProgress, setGmailProgress] = useState<GmailScanProgress | null>(null);
  const [reviews, setReviews] = useState<ReviewSuggestion[]>([]);
  const [reviewCursor, setReviewCursor] = useState<string | null>(null);
  const [reviewHasMore, setReviewHasMore] = useState(false);
  const [reviewLoadingMore, setReviewLoadingMore] = useState(false);
  const [reviewLoadState, setReviewLoadState] = useState<"loading" | "ready" | "unavailable">("loading");
  const [reviewSaving, setReviewSaving] = useState<string | null>(null);
  const [activeThread, setActiveThread] = useState<{ messageId: string; title: string } | null>(null);
  const [recommendationStatus, setRecommendationStatus] = useState("LOADING");
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);

  useEffect(() => {
    let active = true;
    Promise.all([fetch("/api/applications", { cache: "no-store" }), fetch("/api/gmail/status", { cache: "no-store" }), fetch(`/api/gmail/review-items?limit=${REVIEW_PAGE_SIZE}`, { cache: "no-store" })])
      .then(async ([response, gmailResponse, reviewResponse]) => {
        if (gmailResponse.ok) { const status = await gmailResponse.json() as { connected?: boolean; email?: string }; setGmailConnected(status.connected === true); setGmailEmail(status.email ?? ""); }
        let rejectedReviewCompanies: string[] = [];
        if (reviewResponse.ok) { const page = await reviewResponse.json() as ReviewPage; rejectedReviewCompanies = page.rejectedCompanies ?? []; setReviews(page.items); setReviewCursor(page.nextCursor); setReviewHasMore(Boolean(page.nextCursor)); setReviewLoadState("ready"); } else setReviewLoadState("unavailable");
        if (response.status === 401) { setLoadState("auth"); return { items: [], rejectedReviewCompanies }; }
        if (!response.ok) throw new Error("application service unavailable");
        return response.json().then((items: ApiApplication[]) => ({ items, rejectedReviewCompanies }));
      })
      .then(({ items, rejectedReviewCompanies }) => { if (active) { setActions(toActions(items, new Set(rejectedReviewCompanies.map(companyKey)))); setRejectedActions(toRejectedActions(items)); setLoadState((current) => current === "auth" ? current : "ready"); } })
      .catch(() => { if (active) setLoadState("error"); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    fetch("/api/recommendations", { cache: "no-store" }).then(async (response) => {
      const body = await response.json().catch(() => ({})) as { status?: string; items?: Recommendation[] };
      if (!response.ok) throw new Error("recommendations unavailable");
      setRecommendationStatus(body.status ?? "NO_RECOMMENDATIONS");
      setRecommendations(body.items ?? []);
    }).catch(() => setRecommendationStatus("UNAVAILABLE"));
  }, []);

  const loadMoreReviews = async () => {
    if (!reviewCursor || reviewLoadingMore) return;
    setReviewLoadingMore(true);
    try {
      const response = await fetch(`/api/gmail/review-items?limit=${REVIEW_PAGE_SIZE}&cursor=${encodeURIComponent(reviewCursor)}`, { cache: "no-store" });
      if (!response.ok) throw new Error("review queue unavailable");
      const page = await response.json() as ReviewPage;
      setReviews((current) => [...current, ...page.items]);
      setReviewCursor(page.nextCursor);
      setReviewHasMore(Boolean(page.nextCursor));
    } catch { notify("More reviews could not be loaded. Try again shortly."); }
    finally { setReviewLoadingMore(false); }
  };

  const visibleActions = useMemo(() => filter === "All" ? actions : actions.filter((action) => action.kind === filter), [actions, filter]);
  const visibleRejected = filter === "Rejected" ? rejectedActions : [];
  const notify = (message: string) => { setToast(message); if (toastTimer.current) clearTimeout(toastTimer.current); toastTimer.current = window.setTimeout(() => setToast(""), 2600); };
  useEffect(() => () => { if (toastTimer.current) clearTimeout(toastTimer.current); }, []);
  const complete = async (id: string) => {
    try {
      const response = await fetch(`/api/applications/${id}`, { method: "PATCH", headers: { "content-type": "application/json" }, body: JSON.stringify({ status: "CLOSED" }) });
      if (!response.ok) throw new Error("update failed");
      setActions((current) => current.filter((action) => action.id !== id));
      notify("Marked complete — your timeline is up to date.");
    } catch {
      notify("That change was not saved. Try again when the application service is available.");
    }
  };
  const review = (message: string) => notify(message);
  const saveReview = async (suggestionId: string, payload: ReviewPayload) => {
    setReviewSaving(suggestionId);
    try {
      const response = await fetch(`/api/reviews/${encodeURIComponent(suggestionId)}`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(payload) });
      if (!response.ok) throw new Error("review failed");
      setReviews((current) => current.filter((item) => item.suggestion.suggestionId !== suggestionId));
      notify(payload.decision === "DISMISS" ? "Suggestion dismissed. It will stay out of your action queue." : "Suggestion confirmed. It is ready for the next workflow step.");
    } catch { notify("That review was not saved. Nothing was changed."); }
    finally { setReviewSaving(null); }
  };
  const signOut = async () => {
    setSignOutSaving(true);
    try {
      const response = await fetch("/api/auth/logout", { method: "POST" });
      if (!response.ok) throw new Error("sign out failed");
      setActions([]); setGmailConnected(false); setGmailEmail(""); setLoadState("auth"); notify("You have been signed out.");
    } catch { notify("Sign out could not be confirmed. Please try again."); }
    finally { setSignOutSaving(false); }
  };

  const syncGmail = async () => {
    setGmailSyncing(true); setGmailSyncMessage("");
    try {
      const response = await fetch("/api/gmail/sync", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ mode: gmailScanMode }) });
      const body = await response.json() as { started?: boolean; runId?: string; status?: string; imported?: number; error?: { message?: string } };
      if (!response.ok) throw new Error(body.error?.message ?? "Gmail sync failed");
      if (body.started) {
        setGmailSyncMessage("Gmail scan started. Newest conversations are processing first in the background.");
        notify("Gmail scan started — the newest conversations will appear as they are processed.");
        if (body.runId) {
          window.sessionStorage.setItem("jobflow:gmail-run-id", body.runId);
          let terminal = false;
          // A one-day window can contain hundreds of messages. Keep the UI attached
          // long enough to observe the real worker, while still allowing the worker
          // to continue safely after this page is closed.
          for (let attempt = 0; attempt < 180; attempt += 1) {
            await new Promise((resolve) => window.setTimeout(resolve, 2000));
            const statusResponse = await fetch(`/api/gmail/backfills/${encodeURIComponent(body.runId)}`, { cache: "no-store" });
            if (!statusResponse.ok) continue;
            const status = await statusResponse.json() as GmailScanProgress;
            setGmailProgress(status);
            const completed = status.completedBatches ?? 0;
            const total = status.totalBatches ?? 0;
            setGmailSyncMessage(total > 0
              ? `Scanning Gmail — ${status.metadataSeen ?? 0} checked, ${status.candidates ?? 0} candidates, ${status.bodiesFetched ?? 0} bodies opened, ${status.classifiedThreads ?? 0} threads classified. ${completed}/${total} date windows complete.`
              : "Scanning Gmail in the background…");
            const liveReviewResponse = await fetch(`/api/gmail/review-items?limit=${REVIEW_PAGE_SIZE}`, { cache: "no-store" });
            if (liveReviewResponse.ok) { const page = await liveReviewResponse.json() as ReviewPage; setReviews(page.items); setReviewCursor(page.nextCursor); setReviewHasMore(Boolean(page.nextCursor)); }
            if (status.status === "COMPLETED" || status.status === "FAILED") {
              terminal = true;
              window.sessionStorage.removeItem("jobflow:gmail-run-id");
              const [reviewResponse, applicationResponse] = await Promise.all([
                fetch(`/api/gmail/review-items?limit=${REVIEW_PAGE_SIZE}`, { cache: "no-store" }),
                fetch("/api/applications", { cache: "no-store" }),
              ]);
              let rejectedReviewCompanies: string[] = [];
              if (reviewResponse.ok) { const page = await reviewResponse.json() as ReviewPage; rejectedReviewCompanies = page.rejectedCompanies ?? []; setReviews(page.items); setReviewCursor(page.nextCursor); setReviewHasMore(Boolean(page.nextCursor)); }
              if (applicationResponse.ok) { const applications = await applicationResponse.json() as ApiApplication[]; setActions(toActions(applications, new Set(rejectedReviewCompanies.map(companyKey)))); setRejectedActions(toRejectedActions(applications)); }
              setGmailSyncMessage(status.status === "COMPLETED" ? `Gmail scan complete. ${status.autoPromoted ?? 0} records moved forward; ${status.needsReview ?? 0} need your review.` : "Gmail scan finished with an error. Check the connection and try again.");
              break;
            }
          }
          if (!terminal) {
            setGmailSyncMessage("Gmail scan is still running in the background. New review items will appear as windows finish.");
          }
        }
      }
    } catch (error) { setGmailSyncMessage(error instanceof Error ? error.message : "Gmail sync could not be completed."); }
    finally { setGmailSyncing(false); }
  };

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Primary navigation">
        <div className="brand"><div className="brand-mark" aria-hidden="true">JF</div><span className="brand-name">jobflow<span className="brand-period">.</span></span></div>
        <nav className="nav">
          <button className="active" type="button"><NavIcon name="today" /><span>Today</span></button>
          <button type="button" onClick={() => notify("Pipeline view is coming next.")}><NavIcon name="grid" /><span>Applications</span></button>
          <button type="button" onClick={() => notify("Your relationship timeline is ready to connect.")}><NavIcon name="contact" /><span>Contacts</span></button>
          <button type="button" onClick={() => notify("Analytics will learn from your confirmed outcomes.")}><NavIcon name="insight" /><span>Insights</span></button>
        </nav>
        <div className="sidebar-spacer" />
          <div className="sync-card"><div className="sync-title"><span className="sync-kicker"><span className={`sync-dot ${gmailConnected ? "connected" : "disconnected"}`} aria-hidden="true" /> Gmail connection</span><span className="sync-state">{gmailConnected ? "Live" : "Off"}</span></div><p>{gmailConnected ? <>JobFlow checks metadata first and only opens likely job conversations for grounded processing.<strong className="gmail-address">{gmailEmail || "Connected Gmail account"}</strong></> : "Connect a verified account to bring your job conversations into one place."}</p>{gmailSyncMessage && <div className="sync-status" role="status"><span aria-hidden="true">!</span>{gmailSyncMessage}</div>}{gmailConnected && <label className="scan-mode"><span>Scan depth</span><select value={gmailScanMode} onChange={(event) => setGmailScanMode(event.target.value as GmailScanMode)} disabled={gmailSyncing}><option value="FOCUSED">Focused · recommended</option><option value="BROAD">Broad · more recall</option><option value="FULL">Full · all messages</option></select></label>}{gmailProgress && gmailSyncing && <div className="scan-progress" aria-label="Gmail scan stage progress"><span><strong>{gmailProgress.metadataSeen ?? 0}</strong> checked</span><span><strong>{gmailProgress.candidates ?? 0}</strong> candidates</span><span><strong>{gmailProgress.bodiesFetched ?? 0}</strong> bodies opened</span><span><strong>{gmailProgress.classifiedThreads ?? 0}</strong> threads classified</span><small>{gmailProgress.completedBatches ?? 0}/{gmailProgress.totalBatches ?? 0} date windows</small></div>}{loadState === "ready" ? gmailConnected ? <div className="sync-actions"><button className="sync-button" type="button" onClick={syncGmail} disabled={gmailSyncing}><span>{gmailSyncing ? "Scanning Gmail…" : "Scan Gmail"}</span><span aria-hidden="true">→</span></button><a className="sync-link" href="/api/gmail/connect">Reconnect Gmail</a></div> : <a className="sync-button" href="/api/gmail/connect"><span>Connect Gmail</span><span aria-hidden="true">→</span></a> : loadState === "auth" ? <a className="sync-button" href="/api/auth/login"><span>Sign in to connect</span><span aria-hidden="true">→</span></a> : <span className="sync-button" aria-disabled="true"><span>Checking session…</span></span>}</div>
        <div className="profile"><div className="avatar" aria-hidden="true">{gmailEmail ? gmailEmail.slice(0, 1).toUpperCase() : "J"}</div><div className="profile-copy"><strong>Workspace</strong><span>{gmailEmail || "Private JobFlow account"}</span></div>{loadState === "ready" && <button className="profile-action" type="button" onClick={signOut} disabled={signOutSaving} aria-label={signOutSaving ? "Signing out" : "Sign out"} title={signOutSaving ? "Signing out" : "Sign out"}><PowerIcon /></button>}</div>
      </aside>

      <main className="main">
        <header className="topbar">
          <div><p className="eyebrow"><span className="eyebrow-mark" aria-hidden="true" /> Private job-search workspace</p><h1>Move every opportunity<br className="desktop-break" /> forward.</h1><p className="subtitle">A quiet command center for the conversations that can change your career.</p></div>
          <div className="top-actions"><button className="outline-button" type="button" onClick={() => setCaptureOpen(true)} disabled={loadState !== "ready"}>＋ Capture job</button><button className="primary-button" type="button" onClick={() => notify("Draft generation requires a real Gmail thread and verified recipient.")} disabled>Draft unavailable</button></div>
        </header>

        <section className="welcome-panel" aria-labelledby="welcome-heading">
          <div className="welcome-copy"><span className="welcome-label">Your next best action</span><h2 id="welcome-heading">Start with the signal,<br />then make the move.</h2><p>JobFlow turns confirmed emails and captured roles into a small, honest queue. Nothing is invented, sent, or counted until you say so.</p><div className="welcome-actions"><button className="primary-button" type="button" onClick={() => setCaptureOpen(true)} disabled={loadState !== "ready"}>Capture a role <span aria-hidden="true">↗</span></button><span className="trust-note"><span className="trust-check" aria-hidden="true">✓</span> Private by default</span></div></div>
          <div className="signal-orbit" aria-label="JobFlow workflow: capture, connect, follow up"><div className="orbit-line orbit-line-one" /><div className="orbit-line orbit-line-two" /><div className="orbit-center"><span>JF</span><small>your<br />signal</small></div><div className="orbit-node node-top"><span>01</span><strong>Capture</strong></div><div className="orbit-node node-right"><span>02</span><strong>Connect</strong></div><div className="orbit-node node-bottom"><span>03</span><strong>Move</strong></div></div>
        </section>

        <section className="metric-grid" aria-label="Job search summary">
          <Metric icon="↗" label="Active applications" value={loadState === "ready" ? String(actions.length) : "—"} note={loadState === "ready" ? "Confirmed in timeline" : "Not available yet"} />
          <Metric icon="◷" label="Follow-ups due" value={loadState === "ready" ? String(actions.filter((a) => a.kind === "Follow up").length) : "—"} note={loadState === "ready" ? "Ready for your review" : "Not available yet"} />
          <Metric icon="✦" label="Interviews" value="—" note="Waiting for confirmed data" />
          <Metric icon="↔" label="Outreach → reply" value="—" note="Waiting for confirmed outcomes" />
        </section>

        {loadState === "auth" && <div className="state-banner" role="status">Sign in to load your private workspace. JobFlow will not create or display placeholder records. <a href="/api/auth/login">Sign in with your verified account</a></div>}
        {loadState === "error" && <div className="state-banner error" role="alert">Your application service could not be reached. No local fallback data is being shown.</div>}
        {loadState === "loading" && <div className="state-banner">Loading your private application timeline…</div>}

        <div className="content-grid">
          <section className="section-card action-card" aria-labelledby="actions-heading">
            <div className="section-head"><div><span className="section-overline">Your working queue</span><h2 id="actions-heading" className="section-title">Today&apos;s next actions</h2><p className="section-caption">A focused queue built from your confirmed timeline.</p></div><span className="count-pill">{loadState !== "ready" ? "—" : filter === "Rejected" ? rejectedActions.length : visibleActions.length} {filter === "Rejected" ? "rejected" : loadState === "ready" ? "open" : ""}</span></div>
            <div className="filter-row" role="toolbar" aria-label="Filter next actions">{["All", "Follow up", "Review reply", "Prepare", "Rejected"].map((item) => <button key={item} className={`filter ${filter === item ? "active" : ""}`} type="button" aria-pressed={filter === item} onClick={() => setFilter(item)}>{item}</button>)}</div>
            <div className="action-list">{filter === "Rejected" ? visibleRejected.length ? visibleRejected.map((action) => <ActionRow key={action.id} action={action} onComplete={complete} onOpen={() => action.sourceMessageId ? setActiveThread({ messageId: action.sourceMessageId, title: action.title }) : notify("This older record has no stored Gmail message reference, so its conversation cannot be opened.")} />) : loadState === "ready" ? <div className="empty-state queue-empty"><div className="empty-icon" aria-hidden="true">✓</div><strong>No rejected roles.</strong><span>Verified rejection messages will appear here.</span></div> : <QueueEmptyState loadState={loadState} onCapture={() => setCaptureOpen(true)} /> : visibleActions.length ? visibleActions.map((action) => <ActionRow key={action.id} action={action} onComplete={complete} onOpen={() => action.sourceMessageId ? setActiveThread({ messageId: action.sourceMessageId, title: action.title }) : notify("This older record has no stored Gmail message reference, so its conversation cannot be opened.")} />) : <QueueEmptyState loadState={loadState} onCapture={() => setCaptureOpen(true)} />}</div>
          </section>

          <div>
            {reviewLoadState === "unavailable" ? <section className="section-card review-card" aria-labelledby="review-heading"><div className="section-head"><div><span className="section-overline">Evidence inbox</span><h2 id="review-heading" className="section-title">Needs your review</h2></div></div><div className="empty-state">Review queue is unavailable. No local suggestions are shown.</div></section> : reviewLoadState === "loading" ? <section className="section-card review-card" aria-labelledby="review-heading"><div className="section-head"><div><span className="section-overline">Evidence inbox</span><h2 id="review-heading" className="section-title">Needs your review</h2></div></div><div className="empty-state compact-empty"><div className="loading-ring" aria-hidden="true" /><strong>Loading review suggestions…</strong></div></section> : <GmailReviewQueue items={reviews} savingId={reviewSaving} onDecision={saveReview} hasMore={reviewHasMore} loadingMore={reviewLoadingMore} onLoadMore={loadMoreReviews} />}
            <RecommendationPanel status={recommendationStatus} items={recommendations} />
            <section className="section-card insight-card" aria-labelledby="insight-heading"><div className="section-head"><div><span className="section-overline">Pattern library</span><h2 id="insight-heading" className="section-title">Your signals</h2><p className="section-caption">Insights will be calculated from confirmed outcomes.</p></div></div><div className="empty-state compact-empty"><div className="signal-bars" aria-hidden="true"><i /><i /><i /></div><strong>Patterns come later.</strong><span>We will show what is working once your timeline has real outcomes.</span></div></section>
            <section className="section-card stats-card" aria-labelledby="stats-heading"><div className="section-head"><div><span className="section-overline">A monthly pulse</span><h2 id="stats-heading" className="section-title">This month</h2><p className="section-caption">Only confirmed records are counted.</p></div><span className="month-label">{new Date().toLocaleDateString("en-US", { month: "short", year: "numeric" }).toUpperCase()}</span></div><div className="empty-state compact-empty"><div className="empty-icon soft" aria-hidden="true">—</div><strong>No outcome data yet.</strong><span>That is okay. Start with one honest capture.</span></div></section>
          </div>
        </div>
      </main>

      <CaptureWorkflow open={captureOpen} onClose={() => setCaptureOpen(false)} onSaved={(application) => { const action = toAction(application as ApiApplication); if (action) setActions((current) => [action, ...current]); }} notify={notify} />
      {activeThread && <ActionThreadDialog messageId={activeThread.messageId} title={activeThread.title} onClose={() => setActiveThread(null)} />}
      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  );
}

function NavIcon({ name }: { name: "today" | "grid" | "contact" | "insight" }) {
  const paths = { today: "M4 10.5 10 4l6 6.5M5.5 9.5V16h9V9.5", grid: "M4 4h5v5H4zM11 4h5v5h-5zM4 11h5v5H4zM11 11h5v5h-5z", contact: "M10 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM4 16c.5-2.3 2.5-3.5 6-3.5s5.5 1.2 6 3.5", insight: "M4 15V9M8 15V5M12 15v-3M16 15V7" };
  return <svg className="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>;
}
function PowerIcon() { return <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" aria-hidden="true"><path d="M10 3v6" /><path d="M6.1 5.7a6.2 6.2 0 1 0 7.8 0" /></svg>; }
function Metric({ icon, label, value, note }: { icon: string; label: string; value: string; note: string }) { return <div className="metric"><div className="metric-top"><span className="metric-icon" aria-hidden="true">{icon}</span><span className="metric-label">{label}</span></div><div className="metric-value">{value}</div><div className="metric-note">{note}</div></div>; }
function QueueEmptyState({ loadState, onCapture }: { loadState: "loading" | "ready" | "auth" | "error"; onCapture: () => void }) {
  if (loadState === "auth") return <div className="empty-state queue-empty"><div className="empty-icon" aria-hidden="true">→</div><strong>Sign in to see your next actions.</strong><span>Your private queue appears here after you sign in.</span><a className="text-button" href="/api/auth/login">Sign in <span aria-hidden="true">→</span></a></div>;
  if (loadState === "error") return <div className="empty-state queue-empty"><div className="empty-icon soft" aria-hidden="true">!</div><strong>Action queue unavailable.</strong><span>Your application service could not be reached, so nothing is shown.</span></div>;
  if (loadState === "loading") return <div className="empty-state queue-empty"><div className="empty-icon soft" aria-hidden="true">◌</div><strong>Loading your queue…</strong><span>Private data loads only after your session is verified.</span></div>;
  return <div className="empty-state queue-empty"><div className="empty-icon" aria-hidden="true">◌</div><strong>Your queue is clear.</strong><span>Capture a role or scan Gmail to create your first real next action.</span><button className="text-button" type="button" onClick={onCapture}>Capture your first role <span aria-hidden="true">→</span></button></div>;
}
function RecommendationPanel({ status, items }: { status: string; items: Recommendation[] }) {
  return <section className="section-card recommendation-card" aria-labelledby="recommendations-heading"><div className="section-head"><div><span className="section-overline">Official job sources</span><h2 id="recommendations-heading" className="section-title">Recommendations</h2><p className="section-caption">Only attributable feed results with evidence are shown.</p></div></div>{items.length === 0 ? <div className="empty-state compact-empty"><strong>{status === "NO_FEED_CONFIGURED" ? "No job feed connected." : status === "UNAVAILABLE" ? "Recommendations unavailable." : "No recommendations yet."}</strong><span>{status === "FEED_AVAILABLE_NEEDS_PROFILE" ? "Add or index a resume/profile before fit can be scored." : "JobFlow will not invent jobs or fit scores."}</span></div> : <div className="recommendation-list">{items.map((item) => <article className="recommendation-item" key={item.recommendationId}><div><strong>{item.title}</strong><span>{item.company || "Company not provided"}</span></div><span className="action-type amber">Needs profile evidence</span><a href={item.jobUrl} target="_blank" rel="noreferrer">View source ↗</a></article>)}</div>}</section>;
}
function ActionRow({ action, onComplete, onOpen }: { action: Action; onComplete: (id: string) => void; onOpen: () => void }) { return <article className="action-row"><span className={`priority-dot ${action.tone}`} aria-hidden="true" /><div><button className="action-title" type="button" onClick={onOpen}>{action.title}</button><div className="action-meta"><strong>{action.company}</strong><span>{action.role}</span><span>{action.date}</span>{action.direction && <span className={`action-direction ${action.direction.toLowerCase()}`}>{action.direction === "INBOUND" ? "↙ Received" : "↗ Sent"}</span>}</div></div><div><span className={`action-type ${action.tone}`}>{action.kind}</span>{action.kind !== "Rejected" && <button className="complete-button" type="button" onClick={() => onComplete(action.id)}>Mark done</button>}</div></article>; }
function Stat({ label, value, percent }: { label: string; value: string; percent: string }) { return <div><div className="stat-line"><span>{label}</span><strong>{value}</strong></div><div className="bar"><span style={{ width: percent }} /></div></div>; }
function companyKey(company: string | undefined): string {
  return (company ?? "").trim().toLocaleLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

function toActions(applications: ApiApplication[], externallyRejectedCompanies = new Set<string>()): Action[] {
  const rejectedCompanies = new Set(applications.filter(application => application.status === "REJECTED").map(application => companyKey(application.company)).filter(company => company && company !== "unknown company"));
  externallyRejectedCompanies.forEach(company => { if (company && company !== "unknown company") rejectedCompanies.add(company); });
  return applications.map(application => toAction(application, rejectedCompanies)).filter((item): item is Action => item !== null);
}

function toRejectedActions(applications: ApiApplication[]): Action[] {
  return applications.filter(application => application.status === "REJECTED").map(application => ({
    id: application.id,
    title: `Rejected: ${application.role} at ${application.company}`,
    company: application.company,
    role: application.role,
    date: `Updated ${new Date(application.updatedAt).toLocaleDateString()}`,
    kind: "Rejected" as const,
    tone: "blue" as const,
    direction: application.sourceDirection,
    sourceMessageId: application.sourceMessageId,
  }));
}

function toAction(application: ApiApplication, rejectedCompanies = new Set<string>()): Action | null {
  if (application.status === "REJECTED" || application.status === "CLOSED" || rejectedCompanies.has(companyKey(application.company))) return null;
  const isCaptured = application.status === "CAPTURED";
  const preparing = application.status === "INTERVIEW";
  const title = isCaptured ? `Review ${application.role} capture` : preparing ? `Prepare for ${application.company}` : application.sourceDirection === "INBOUND" ? `Reply to ${application.company}` : `Follow up with ${application.company}`;
  return { id: application.id, title, company: application.company, role: application.role, date: isCaptured ? "Needs review" : `Updated ${new Date(application.updatedAt).toLocaleDateString()}`, kind: isCaptured ? "Review reply" : preparing ? "Prepare" : "Follow up", tone: isCaptured || preparing ? "blue" : "amber", direction: application.sourceDirection, sourceMessageId: application.sourceMessageId };
}

function ActionThreadDialog({ messageId, title, onClose }: { messageId: string; title: string; onClose: () => void }) {
  const [thread, setThread] = useState<LiveThread | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { let active = true; fetch(`/api/gmail/messages/${encodeURIComponent(messageId)}/thread`, { cache: "no-store" }).then(async response => { const body = await response.json() as { thread?: LiveThread; error?: { message?: string } }; if (!response.ok) throw new Error(body.error?.message ?? "The Gmail conversation could not be loaded"); if (active) setThread(body.thread ?? null); }).catch(reason => { if (active) setError(reason instanceof Error ? reason.message : "The Gmail conversation could not be loaded"); }); return () => { active = false; }; }, [messageId]);
  useEffect(() => { const onKeyDown = (event: KeyboardEvent) => { if (event.key === "Escape") onClose(); }; document.addEventListener("keydown", onKeyDown); return () => document.removeEventListener("keydown", onKeyDown); }, [onClose]);
  return <div className="overlay" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><section className="modal thread-modal" role="dialog" aria-modal="true" aria-labelledby="action-thread-heading"><div className="modal-head"><div><span className="section-overline">Live Gmail conversation</span><h2 id="action-thread-heading">{title}</h2><p className="section-caption">Loaded on demand; raw content is not persisted by JobFlow.</p></div><button className="close" aria-label="Close conversation" type="button" onClick={onClose}>×</button></div><div className="modal-body">{error ? <p className="thread-detail-error" role="alert">{error}</p> : !thread ? <p className="thread-detail-empty">Loading conversation…</p> : <div className="live-thread">{thread.messages.map(message => <div className={`live-message ${message.direction === "OUTBOUND" ? "sent" : "received"}`} key={message.messageId}><div className="live-message-head"><span className={`message-direction ${message.direction === "OUTBOUND" ? "outbound" : "inbound"}`}>{message.direction === "OUTBOUND" ? "↗ Sent" : "↙ Received"}</span><time>{message.receivedAt ? new Date(message.receivedAt).toLocaleString() : "Time unavailable"}</time></div><strong>{message.subject ?? "(no subject)"}</strong><p>{formatGmailMessageBody(message.body)}</p></div>)}</div>}</div><div className="modal-foot"><button className="outline-button" type="button" onClick={onClose}>Close</button></div></section></div>;
}
