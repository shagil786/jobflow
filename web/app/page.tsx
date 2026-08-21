"use client";

import { useEffect, useMemo, useState } from "react";

type Action = {
  id: string;
  title: string;
  company: string;
  role: string;
  date: string;
  kind: "Follow up" | "Review reply" | "Prepare";
  tone: "amber" | "blue" | "green";
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
};

type ReviewSuggestion = {
  connectionId: string;
  suggestion: { suggestionId: string; messageId: string; threadId: string; intent: string; confidence: number; company?: { value?: string }; role?: { value?: string }; applicationDate?: { value?: string }; contact?: { value?: string }; missingFields: string[] };
};
type ReviewPayload = { decision: "ACCEPT" | "CORRECT" | "DISMISS"; company?: string; role?: string };

export default function Home() {
  const [actions, setActions] = useState<Action[]>([]);
  const [filter, setFilter] = useState("All");
  const [toast, setToast] = useState("");
  const [loadState, setLoadState] = useState<"loading" | "ready" | "auth" | "error">("loading");
  const [captureOpen, setCaptureOpen] = useState(false);
  const [captureUrl, setCaptureUrl] = useState("");
  const [captureTitle, setCaptureTitle] = useState("");
  const [captureCompany, setCaptureCompany] = useState("");
  const [captureSaving, setCaptureSaving] = useState(false);
  const [signOutSaving, setSignOutSaving] = useState(false);
  const [gmailConnected, setGmailConnected] = useState(false);
  const [gmailSyncing, setGmailSyncing] = useState(false);
  const [gmailSyncMessage, setGmailSyncMessage] = useState("");
  const [reviews, setReviews] = useState<ReviewSuggestion[]>([]);
  const [reviewLoadState, setReviewLoadState] = useState<"loading" | "ready" | "unavailable">("loading");
  const [reviewSaving, setReviewSaving] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all([fetch("/api/applications", { cache: "no-store" }), fetch("/api/gmail/status", { cache: "no-store" }), fetch("/api/reviews", { cache: "no-store" })])
      .then(async ([response, gmailResponse, reviewResponse]) => {
        if (gmailResponse.ok) { const status = await gmailResponse.json() as { connected?: boolean }; setGmailConnected(status.connected === true); }
        if (reviewResponse.ok) { setReviews(await reviewResponse.json() as ReviewSuggestion[]); setReviewLoadState("ready"); } else setReviewLoadState("unavailable");
        if (response.status === 401) { setLoadState("auth"); return []; }
        if (!response.ok) throw new Error("application service unavailable");
        return response.json() as Promise<ApiApplication[]>;
      })
      .then((items) => { if (active) { setActions(items.map(toAction)); setLoadState((current) => current === "auth" ? current : "ready"); } })
      .catch(() => { if (active) setLoadState("error"); });
    return () => { active = false; };
  }, []);

  const visibleActions = useMemo(() => filter === "All" ? actions : actions.filter((action) => action.kind === filter), [actions, filter]);
  const notify = (message: string) => { setToast(message); window.setTimeout(() => setToast(""), 2600); };
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
      setActions([]); setGmailConnected(false); setLoadState("auth"); notify("You have been signed out.");
    } catch { notify("Sign out could not be confirmed. Please try again."); }
    finally { setSignOutSaving(false); }
  };

  const syncGmail = async () => {
    setGmailSyncing(true); setGmailSyncMessage("");
    try {
      const response = await fetch("/api/gmail/sync", { method: "POST" });
      const body = await response.json() as { imported?: number; error?: { message?: string } };
      if (!response.ok) throw new Error(body.error?.message ?? "Gmail sync failed");
      const imported = typeof body.imported === "number" ? body.imported : 0;
      setGmailSyncMessage(imported ? `Synced ${imported} new message${imported === 1 ? "" : "s"}.` : "Gmail is already up to date.");
      notify(imported ? `Gmail synced — ${imported} new message${imported === 1 ? "" : "s"} found.` : "Gmail is already up to date.");
    } catch (error) { setGmailSyncMessage(error instanceof Error ? error.message : "Gmail sync could not be completed."); }
    finally { setGmailSyncing(false); }
  };

  const captureJob = async () => {
    if (!captureUrl || !captureTitle) { notify("Add a job URL and role title first."); return; }
    setCaptureSaving(true);
    try {
      const response = await fetch("/api/applications/capture", {
        method: "POST",
        headers: { "content-type": "application/json", "x-idempotency-key": `ui:${captureUrl}` },
        body: JSON.stringify({ url: captureUrl, title: captureTitle, company: captureCompany, role: captureTitle, source: "web-capture", capturedAt: new Date().toISOString() }),
      });
      if (!response.ok) throw new Error("capture failed");
      const application = await response.json() as ApiApplication;
      setActions((current) => [toAction(application), ...current]);
      setCaptureOpen(false);
      setCaptureUrl(""); setCaptureTitle(""); setCaptureCompany("");
      notify("Job saved to your application timeline.");
    } catch {
      notify("Capture could not be saved. Check your signed-in session and try again.");
    } finally { setCaptureSaving(false); }
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
        <div className="sync-card"><div className="sync-title"><span className="sync-kicker"><span className={`sync-dot ${gmailConnected ? "connected" : "disconnected"}`} aria-hidden="true" /> Gmail connection</span><span className="sync-state">{gmailConnected ? "Live" : "Off"}</span></div><p>{gmailConnected ? "Only messages labeled JobFlow/Track enter your workspace." : "Connect a verified account to bring your job conversations into one place."}</p>{gmailSyncMessage && <div className="sync-status" role="status"><span aria-hidden="true">!</span>{gmailSyncMessage}</div>}{loadState === "ready" ? gmailConnected ? <div className="sync-actions"><button className="sync-button" type="button" onClick={syncGmail} disabled={gmailSyncing}><span>{gmailSyncing ? "Syncing Gmail…" : "Sync now"}</span><span aria-hidden="true">→</span></button><a className="sync-link" href="/api/gmail/connect">Manage connection</a></div> : <a className="sync-button" href="/api/gmail/connect"><span>Connect Gmail</span><span aria-hidden="true">→</span></a> : loadState === "auth" ? <a className="sync-button" href="/api/auth/login"><span>Sign in to connect</span><span aria-hidden="true">→</span></a> : <span className="sync-button" aria-disabled="true"><span>Checking session…</span></span>}</div>
        <div className="profile"><div className="avatar" aria-hidden="true">?</div><div className="profile-copy"><strong>Signed-in account</strong><br /><span>Private workspace</span></div>{loadState === "ready" && <button className="profile-action" type="button" onClick={signOut} disabled={signOutSaving} aria-label={signOutSaving ? "Signing out" : "Sign out"}><span aria-hidden="true">↪</span>{signOutSaving ? "Signing out…" : "Sign out"}</button>}</div>
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
            <div className="section-head"><div><span className="section-overline">Your working queue</span><h2 id="actions-heading" className="section-title">Today&apos;s next actions</h2><p className="section-caption">A focused queue built from your confirmed timeline.</p></div><span className="count-pill">{actions.length} open</span></div>
            <div className="filter-row" role="toolbar" aria-label="Filter next actions">{["All", "Follow up", "Review reply", "Prepare"].map((item) => <button key={item} className={`filter ${filter === item ? "active" : ""}`} type="button" onClick={() => setFilter(item)}>{item}</button>)}</div>
            <div className="action-list">{visibleActions.length ? visibleActions.map((action) => <ActionRow key={action.id} action={action} onComplete={complete} onOpen={() => notify(action.kind === "Review reply" ? "Reply context is not connected yet." : "Draft generation requires a real Gmail thread and verified recipient.")} />) : <div className="empty-state queue-empty"><div className="empty-icon" aria-hidden="true">◌</div><strong>Your queue is clear.</strong><span>Capture a role or sync JobFlow/Track to create your first real next action.</span><button className="text-button" type="button" onClick={() => setCaptureOpen(true)} disabled={loadState !== "ready"}>Capture your first role <span aria-hidden="true">→</span></button></div>}</div>
          </section>

          <div>
            <section className="section-card review-card" aria-labelledby="review-heading"><div className="section-head"><div><span className="section-overline">Evidence inbox</span><h2 id="review-heading" className="section-title">Needs your review</h2><p className="section-caption">Suggestions stay out of your timeline until you confirm them.</p></div><span className="count-pill">{reviews.length} items</span></div>{reviewLoadState === "unavailable" ? <div className="empty-state">Review queue is unavailable. No local suggestions are shown.</div> : reviews.length ? <div className="review-list">{reviews.map((item) => <ReviewRow key={item.suggestion.suggestionId} item={item} saving={reviewSaving === item.suggestion.suggestionId} onDecision={saveReview} />)}</div> : <div className="empty-state compact-empty"><div className="empty-icon soft" aria-hidden="true">✦</div><strong>No evidence waiting.</strong><span>When JobFlow finds a scoped message, it will appear here before it becomes a record.</span></div>}</section>
            <section className="section-card insight-card" aria-labelledby="insight-heading"><div className="section-head"><div><span className="section-overline">Pattern library</span><h2 id="insight-heading" className="section-title">Your signals</h2><p className="section-caption">Insights will be calculated from confirmed outcomes.</p></div></div><div className="empty-state compact-empty"><div className="signal-bars" aria-hidden="true"><i /><i /><i /></div><strong>Patterns come later.</strong><span>We will show what is working once your timeline has real outcomes.</span></div></section>
            <section className="section-card stats-card" aria-labelledby="stats-heading"><div className="section-head"><div><span className="section-overline">A monthly pulse</span><h2 id="stats-heading" className="section-title">This month</h2><p className="section-caption">Only confirmed records are counted.</p></div><span className="month-label">AUG 2026</span></div><div className="empty-state compact-empty"><div className="empty-icon soft" aria-hidden="true">—</div><strong>No outcome data yet.</strong><span>That is okay. Start with one honest capture.</span></div></section>
          </div>
        </div>
      </main>

      {captureOpen && <div className="overlay" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !captureSaving) setCaptureOpen(false); }}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="capture-heading"><div className="modal-head"><div><h2 id="capture-heading">Capture a job</h2><p className="section-caption">Save visible job details for review before creating an application.</p></div><button className="close" aria-label="Close capture" type="button" onClick={() => !captureSaving && setCaptureOpen(false)}>×</button></div><div className="modal-body"><label className="modal-label" htmlFor="capture-url">Job URL</label><input className="draft-input" id="capture-url" placeholder="https://company.com/jobs/..." value={captureUrl} onChange={(event) => setCaptureUrl(event.target.value)} /><label className="modal-label" htmlFor="capture-title">Role title</label><input className="draft-input" id="capture-title" placeholder="Senior Frontend Engineer" value={captureTitle} onChange={(event) => setCaptureTitle(event.target.value)} /><label className="modal-label" htmlFor="capture-company">Company (optional)</label><input className="draft-input" id="capture-company" placeholder="Company name" value={captureCompany} onChange={(event) => setCaptureCompany(event.target.value)} /><div className="warning">Capture stores the URL and fields you confirm. It does not apply, send, or read unrelated browsing history.</div></div><div className="modal-foot"><button className="outline-button" type="button" onClick={() => setCaptureOpen(false)} disabled={captureSaving}>Cancel</button><button className="primary-button" type="button" onClick={captureJob} disabled={captureSaving}>{captureSaving ? "Saving…" : "Save for review"}</button></div></section></div>}
      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  );
}

function NavIcon({ name }: { name: "today" | "grid" | "contact" | "insight" }) {
  const paths = { today: "M4 10.5 10 4l6 6.5M5.5 9.5V16h9V9.5", grid: "M4 4h5v5H4zM11 4h5v5h-5zM4 11h5v5H4zM11 11h5v5h-5z", contact: "M10 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM4 16c.5-2.3 2.5-3.5 6-3.5s5.5 1.2 6 3.5", insight: "M4 15V9M8 15V5M12 15v-3M16 15V7" };
  return <svg className="nav-icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>;
}
function Metric({ icon, label, value, note }: { icon: string; label: string; value: string; note: string }) { return <div className="metric"><div className="metric-top"><span className="metric-icon" aria-hidden="true">{icon}</span><span className="metric-label">{label}</span></div><div className="metric-value">{value}</div><div className="metric-note">{note}</div></div>; }
function ActionRow({ action, onComplete, onOpen }: { action: Action; onComplete: (id: string) => void; onOpen: () => void }) { return <article className="action-row"><span className={`priority-dot ${action.tone}`} aria-hidden="true" /><div><button className="action-title" type="button" onClick={onOpen}>{action.title}</button><div className="action-meta"><strong>{action.company}</strong><span>{action.role}</span><span>{action.date}</span></div></div><div><span className={`action-type ${action.tone}`}>{action.kind}</span><button className="complete-button" type="button" onClick={() => onComplete(action.id)}>Mark done</button></div></article>; }
function ReviewRow({ item, saving, onDecision }: { item: ReviewSuggestion; saving: boolean; onDecision: (id: string, payload: ReviewPayload) => void }) {
  const suggestion = item.suggestion;
  const [editing, setEditing] = useState(false);
  const [company, setCompany] = useState(suggestion.company?.value ?? "");
  const [role, setRole] = useState(suggestion.role?.value ?? "");
  return <article className="review-row"><div><strong>{suggestion.company?.value ?? "Unknown company"}</strong><span className="review-intent">{suggestion.intent.replaceAll("_", " ").toLowerCase()}</span><p>{suggestion.role?.value ?? "Role needs confirmation"} · {Math.round(suggestion.confidence * 100)}% confidence</p><small>Message {suggestion.messageId} · {suggestion.missingFields.length ? `Missing: ${suggestion.missingFields.join(", ")}` : "Fields are complete"}</small>{editing && <div className="review-edit"><label>Company<input value={company} onChange={(event) => setCompany(event.target.value)} /></label><label>Role<input value={role} onChange={(event) => setRole(event.target.value)} /></label></div>}</div><div className="review-actions">{editing ? <button className="primary-button" type="button" disabled={saving} onClick={() => onDecision(suggestion.suggestionId, { decision: "CORRECT", company, role })}>{saving ? "Saving…" : "Save correction"}</button> : <><button className="outline-button" type="button" disabled={saving} onClick={() => onDecision(suggestion.suggestionId, { decision: "DISMISS" })}>Dismiss</button><button className="outline-button" type="button" disabled={saving} onClick={() => setEditing(true)}>Correct</button><button className="primary-button" type="button" disabled={saving} onClick={() => onDecision(suggestion.suggestionId, { decision: "ACCEPT" })}>{saving ? "Saving…" : "Confirm"}</button></>}</div></article>;
}
function Stat({ label, value, percent }: { label: string; value: string; percent: string }) { return <div><div className="stat-line"><span>{label}</span><strong>{value}</strong></div><div className="bar"><span style={{ width: percent }} /></div></div>; }
function toAction(application: ApiApplication): Action {
  const isCaptured = application.status === "CAPTURED";
  return { id: application.id, title: isCaptured ? `Review ${application.role} capture` : `Follow up with ${application.company}`, company: application.company, role: application.role, date: isCaptured ? "Needs review" : `Updated ${new Date(application.updatedAt).toLocaleDateString()}`, kind: isCaptured ? "Review reply" : "Follow up", tone: isCaptured ? "blue" : "amber" };
}
