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

  useEffect(() => {
    let active = true;
    Promise.all([fetch("/api/applications", { cache: "no-store" }), fetch("/api/gmail/status", { cache: "no-store" })])
      .then(async ([response, gmailResponse]) => {
        if (gmailResponse.ok) { const status = await gmailResponse.json() as { connected?: boolean }; setGmailConnected(status.connected === true); }
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
        <div className="brand"><div className="brand-mark">JF</div><span className="brand-name">jobflow</span></div>
        <nav className="nav">
          <button className="active" type="button"><span className="nav-icon">⌂</span><span>Today</span></button>
          <button type="button" onClick={() => notify("Pipeline view is coming next.")}><span className="nav-icon">▦</span><span>Applications</span></button>
          <button type="button" onClick={() => notify("Your relationship timeline is ready to connect.")}><span className="nav-icon">◎</span><span>Contacts</span></button>
          <button type="button" onClick={() => notify("Analytics will learn from your confirmed outcomes.")}><span className="nav-icon">↗</span><span>Insights</span></button>
        </nav>
        <div className="sidebar-spacer" />
        <div className="sync-card"><div className="sync-title"><span>Gmail scope</span><span className={`sync-dot ${gmailConnected ? "connected" : "disconnected"}`} aria-label={gmailConnected ? "Connected" : "Not connected"} /></div><p>{gmailConnected ? "Gmail is connected. Sync is limited to the JobFlow/Track label." : "Connect a verified account and choose the JobFlow/Track label before importing anything."}</p>{gmailSyncMessage && <div className="sync-status" role="status">{gmailSyncMessage}</div>}{loadState === "ready" ? gmailConnected ? <div className="sync-actions"><button className="sync-button" type="button" onClick={syncGmail} disabled={gmailSyncing}>{gmailSyncing ? "Syncing Gmail…" : "Sync now"}</button><a className="sync-link" href="/api/gmail/connect">Manage connection</a></div> : <a className="sync-button" href="/api/gmail/connect">Connect Gmail</a> : loadState === "auth" ? <a className="sync-button" href="/api/auth/login">Sign in to connect</a> : <span className="sync-button" aria-disabled="true">Checking session…</span>}</div>
        <div className="profile"><div className="avatar">?</div><div><strong>Signed-in account</strong><br /><span>Private workspace</span></div></div>
      </aside>

      <main className="main">
        <header className="topbar">
          <div><p className="eyebrow">Private job-search workspace</p><h1>Make the next move count.</h1><p className="subtitle">You have {actions.length} confirmed conversations worth your attention.</p></div>
          <div className="top-actions"><button className="outline-button" type="button" onClick={() => setCaptureOpen(true)} disabled={loadState !== "ready"}>＋ Capture job</button><button className="primary-button" type="button" onClick={() => notify("Draft generation requires a real Gmail thread and verified recipient.")} disabled>Draft unavailable</button>{loadState === "ready" && <button className="outline-button" type="button" onClick={signOut} disabled={signOutSaving}>{signOutSaving ? "Signing out…" : "Sign out"}</button>}</div>
        </header>

        <section className="metric-grid" aria-label="Job search summary">
          <Metric label="Active applications" value={loadState === "ready" ? String(actions.length) : "—"} note={loadState === "ready" ? "From your timeline" : "Not available yet"} />
          <Metric label="Follow-ups due" value={loadState === "ready" ? String(actions.filter((a) => a.kind === "Follow up").length) : "—"} note={loadState === "ready" ? "From your timeline" : "Not available yet"} />
          <Metric label="Interviews" value="—" note="Requires confirmed email data" />
          <Metric label="Outreach → reply" value="—" note="Requires confirmed outcomes" />
        </section>

        {loadState === "auth" && <div className="state-banner" role="status">Sign in to load your private workspace. JobFlow will not create or display placeholder records. <a href="/api/auth/login">Sign in with your verified account</a></div>}
        {loadState === "error" && <div className="state-banner error" role="alert">Your application service could not be reached. No local fallback data is being shown.</div>}
        {loadState === "loading" && <div className="state-banner">Loading your private application timeline…</div>}

        <div className="content-grid">
          <section className="section-card" aria-labelledby="actions-heading">
            <div className="section-head"><div><h2 id="actions-heading" className="section-title">Today&apos;s next actions</h2><p className="section-caption">A focused queue built from your confirmed timeline.</p></div><span className="count-pill">{actions.length} open</span></div>
            <div className="filter-row" role="toolbar" aria-label="Filter next actions">{["All", "Follow up", "Review reply", "Prepare"].map((item) => <button key={item} className={`filter ${filter === item ? "active" : ""}`} type="button" onClick={() => setFilter(item)}>{item}</button>)}</div>
            <div className="action-list">{visibleActions.length ? visibleActions.map((action) => <ActionRow key={action.id} action={action} onComplete={complete} onOpen={() => notify(action.kind === "Review reply" ? "Reply context is not connected yet." : "Draft generation requires a real Gmail thread and verified recipient.")} />) : <div className="empty-state">Nothing in this queue. You can switch filters or capture another job.</div>}</div>
          </section>

          <div>
            <section className="section-card review-card" aria-labelledby="review-heading"><div className="section-head"><div><h2 id="review-heading" className="section-title">Needs your review</h2><p className="section-caption">AI suggestions will appear here only after real scoped email data is processed.</p></div><span className="count-pill">0 items</span></div><div className="empty-state">No unreviewed suggestions.</div></section>
            <section className="section-card insight-card" aria-labelledby="insight-heading"><div className="section-head"><div><h2 id="insight-heading" className="section-title">Your signals</h2><p className="section-caption">Insights will be calculated from confirmed outcomes.</p></div></div><div className="empty-state">Capture and confirm applications to see real patterns.</div></section>
            <section className="section-card stats-card" aria-labelledby="stats-heading"><div className="section-head"><div><h2 id="stats-heading" className="section-title">This month</h2><p className="section-caption">Only confirmed records are counted.</p></div></div><div className="empty-state">No outcome data yet.</div></section>
          </div>
        </div>
      </main>

      {captureOpen && <div className="overlay" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !captureSaving) setCaptureOpen(false); }}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="capture-heading"><div className="modal-head"><div><h2 id="capture-heading">Capture a job</h2><p className="section-caption">Save visible job details for review before creating an application.</p></div><button className="close" aria-label="Close capture" type="button" onClick={() => !captureSaving && setCaptureOpen(false)}>×</button></div><div className="modal-body"><label className="modal-label" htmlFor="capture-url">Job URL</label><input className="draft-input" id="capture-url" placeholder="https://company.com/jobs/..." value={captureUrl} onChange={(event) => setCaptureUrl(event.target.value)} /><label className="modal-label" htmlFor="capture-title">Role title</label><input className="draft-input" id="capture-title" placeholder="Senior Frontend Engineer" value={captureTitle} onChange={(event) => setCaptureTitle(event.target.value)} /><label className="modal-label" htmlFor="capture-company">Company (optional)</label><input className="draft-input" id="capture-company" placeholder="Company name" value={captureCompany} onChange={(event) => setCaptureCompany(event.target.value)} /><div className="warning">Capture stores the URL and fields you confirm. It does not apply, send, or read unrelated browsing history.</div></div><div className="modal-foot"><button className="outline-button" type="button" onClick={() => setCaptureOpen(false)} disabled={captureSaving}>Cancel</button><button className="primary-button" type="button" onClick={captureJob} disabled={captureSaving}>{captureSaving ? "Saving…" : "Save for review"}</button></div></section></div>}
      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  );
}

function Metric({ label, value, note }: { label: string; value: string; note: string }) { return <div className="metric"><div className="metric-label">{label}</div><div className="metric-value">{value}</div><div className="metric-note">{note}</div></div>; }
function ActionRow({ action, onComplete, onOpen }: { action: Action; onComplete: (id: string) => void; onOpen: () => void }) { return <article className="action-row"><span className={`priority-dot ${action.tone}`} aria-hidden="true" /><div><button className="action-title" type="button" onClick={onOpen}>{action.title}</button><div className="action-meta"><strong>{action.company}</strong><span>{action.role}</span><span>{action.date}</span></div></div><div><span className={`action-type ${action.tone}`}>{action.kind}</span><button className="complete-button" type="button" onClick={() => onComplete(action.id)}>Mark done</button></div></article>; }
function Stat({ label, value, percent }: { label: string; value: string; percent: string }) { return <div><div className="stat-line"><span>{label}</span><strong>{value}</strong></div><div className="bar"><span style={{ width: percent }} /></div></div>; }
function toAction(application: ApiApplication): Action {
  const isCaptured = application.status === "CAPTURED";
  return { id: application.id, title: isCaptured ? `Review ${application.role} capture` : `Follow up with ${application.company}`, company: application.company, role: application.role, date: isCaptured ? "Needs review" : `Updated ${new Date(application.updatedAt).toLocaleDateString()}`, kind: isCaptured ? "Review reply" : "Follow up", tone: isCaptured ? "blue" : "amber" };
}
