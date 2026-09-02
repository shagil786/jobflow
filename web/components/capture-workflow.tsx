"use client";

import { useEffect, useState } from "react";

type Application = { id: string; company?: string; role?: string; jobUrl?: string; location?: string };
type Contact = {
  id?: string;
  contactId?: string;
  name?: string;
  role?: string;
  company?: string;
  email?: string;
  profileUrl?: string;
  sourceUrl?: string;
  sourceMessageId?: string;
  evidence?: string;
  confidence?: number;
  verificationStatus?: "DISCOVERED" | "VERIFIED" | "NEEDS_REVIEW" | "REJECTED" | "EXPIRED";
  allowedUse?: string;
  preselected?: boolean;
};
const normalizeContact = (value: Contact): Contact => ({ ...value, id: value.id ?? value.contactId });
type ResumeVersion = { id: string; name?: string; version?: string; updatedAt?: string };
type Enrichment = { status?: "CAPTURED" | "QUEUED" | "RUNNING" | "PUBLIC_SEARCH" | "GMAIL_SEARCH" | "PROVIDER_SEARCH" | "VERIFYING" | "READY" | "COMPLETED" | "CONTACTS_READY" | "NO_VERIFIED_CONTACT" | "FAILED" | "PROVIDER_UNAVAILABLE"; provider?: string; message?: string };
type Draft = { id: string; recipient: string; subject: string; body: string; sent: false; gmailDraftId?: string; evidence?: Array<{ text: string; source?: string }> };
type DraftResponse = Partial<Draft> & { draftId?: string; error?: { message?: string } };

export function CaptureWorkflow({ open, onClose, onSaved, notify }: { open: boolean; onClose: () => void; onSaved: (application: Application) => void; notify: (message: string) => void }) {
  const [step, setStep] = useState<"capture" | "enrich" | "contacts" | "draft">("capture");
  const [url, setUrl] = useState("");
  const [title, setTitle] = useState("");
  const [company, setCompany] = useState("");
  const [role, setRole] = useState("");
  const [location, setLocation] = useState("");
  const [description, setDescription] = useState("");
  // Stable for retries within this modal, but new for a fresh capture. Using
  // the URL alone made correcting an existing LinkedIn capture look like an
  // idempotency conflict.
  const [captureIdempotencyKey, setCaptureIdempotencyKey] = useState(() => `ui:${crypto.randomUUID()}`);
  const [application, setApplication] = useState<Application | null>(null);
  const [enrichment, setEnrichment] = useState<Enrichment | null>(null);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null);
  const [resumes, setResumes] = useState<ResumeVersion[]>([]);
  const [resumeUploading, setResumeUploading] = useState(false);
  const [resumeId, setResumeId] = useState("");
  const [draft, setDraft] = useState<Draft | null>(null);
  const [instructions, setInstructions] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === "Escape" && !saving) onClose(); };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, onClose, saving]);

  useEffect(() => {
    if (!open || !application || step !== "contacts") return;
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const load = async () => {
      try {
        const [response, progressResponse] = await Promise.all([
          fetch(`/api/applications/${encodeURIComponent(application.id)}/contacts`, { cache: "no-store" }),
          fetch(`/api/applications/${encodeURIComponent(application.id)}/enrichment`, { cache: "no-store" }),
        ]);
        const body = await response.json().catch(() => ({})) as Contact[] | { items?: Contact[]; contacts?: Contact[]; error?: { message?: string } };
        const progress = await progressResponse.json().catch(() => ({})) as Enrichment & { error?: { message?: string } };
        // The worker can commit the run a moment before the first contacts
        // read is visible. Keep polling through that short race instead of
        // leaving a stale generic error after a later successful response.
        if (!response.ok && progressResponse.ok && progress.status !== "FAILED") {
          if (active) {
            setEnrichment((current) => ({ ...current, status: progress.status ?? "RUNNING" }));
            timer = setTimeout(() => void load(), 1000);
          }
          return;
        }
        if (!response.ok) throw new Error(("error" in body ? body.error?.message : undefined) ?? "Contact results are unavailable.");
        if (active) {
          const status = progress.status ?? "RUNNING";
          setError("");
          setContacts((Array.isArray(body) ? body as unknown as Contact[] : body.items ?? body.contacts ?? []).map(normalizeContact));
          setEnrichment((current) => ({ ...current, status }));
          if (status !== "READY" && status !== "COMPLETED" && status !== "CONTACTS_READY" && status !== "NO_VERIFIED_CONTACT" && status !== "FAILED" && status !== "PROVIDER_UNAVAILABLE") timer = setTimeout(() => void load(), 2000);
        }
      } catch (reason) { if (active) setError(reason instanceof Error ? reason.message : "Contact results are unavailable."); }
    };
    void load();
    return () => { active = false; if (timer) clearTimeout(timer); };
  }, [open, application, step]);

  const reset = () => { setStep("capture"); setUrl(""); setTitle(""); setCompany(""); setRole(""); setLocation(""); setDescription(""); setCaptureIdempotencyKey(`ui:${crypto.randomUUID()}`); setApplication(null); setEnrichment(null); setContacts([]); setSelectedContact(null); setResumes([]); setResumeId(""); setDraft(null); setInstructions(""); setError(""); };
  const close = () => { if (saving) return; reset(); onClose(); };

  const uploadResume = async (file: File) => {
    setResumeUploading(true); setError("");
    try {
      const form = new FormData(); form.append("file", file);
      const response = await fetch("/api/resumes/versions", { method: "POST", body: form });
      const body = await response.json().catch(() => ({})) as ResumeVersion & { error?: { message?: string } };
      if (!response.ok || !body.id) throw new Error(body.error?.message ?? "Resume upload could not be completed.");
      setResumes((current) => [body, ...current]); setResumeId(body.id); notify("Resume version uploaded privately.");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Resume upload could not be completed."); }
    finally { setResumeUploading(false); }
  };

  const retryEnrichment = async () => {
    if (!application) return;
    setSaving(true); setError(""); setEnrichment({ status: "QUEUED" }); setStep("enrich");
    try {
      const response = await fetch(`/api/applications/${encodeURIComponent(application.id)}/enrichment`, { method: "POST", headers: { "content-type": "application/json", "x-idempotency-key": `enrich:${application.id}` }, body: JSON.stringify({ publicSources: [{ sourceUrl: application.jobUrl ?? url.trim(), content: description.trim(), title: title.trim() }] }) });
      const body = await response.json().catch(() => ({})) as Enrichment & { error?: { message?: string } };
      if (!response.ok) throw new Error(body.error?.message ?? "Contact discovery could not be started.");
      setEnrichment(body); setStep("contacts");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Contact discovery could not be started."); setEnrichment({ status: "FAILED" }); setStep("contacts"); }
    finally { setSaving(false); }
  };

  const capture = async () => {
    if (!url.trim() || !title.trim() || !role.trim()) { setError("Job URL, role title, and role are required."); return; }
    setSaving(true); setError("");
    let savedApplication: Application | null = null;
    try {
      const response = await fetch("/api/applications/capture", { method: "POST", headers: { "content-type": "application/json", "x-idempotency-key": captureIdempotencyKey }, body: JSON.stringify({ url: url.trim(), title: title.trim(), company: company.trim(), role: role.trim(), location: location.trim(), descriptionPreview: description.trim(), source: "web-capture", capturedAt: new Date().toISOString() }) });
      const body = await response.json().catch(() => ({})) as Application & { error?: { message?: string } };
      if (!response.ok || !body.id) throw new Error(body.error?.message ?? "Capture could not be saved.");
      savedApplication = body;
      setApplication(body); onSaved(body); setStep("enrich");
      const enrichmentResponse = await fetch(`/api/applications/${encodeURIComponent(body.id)}/enrichment`, { method: "POST", headers: { "content-type": "application/json", "x-idempotency-key": `enrich:${body.id}` }, body: JSON.stringify({ publicSources: [{ sourceUrl: url.trim(), content: description.trim(), title: title.trim() }] }) });
      const enrichmentBody = await enrichmentResponse.json().catch(() => ({})) as Enrichment & { error?: { message?: string } };
      if (!enrichmentResponse.ok) throw new Error(enrichmentBody.error?.message ?? "Contact discovery could not be started.");
      setEnrichment(enrichmentBody); setStep("contacts");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Contact discovery could not be started.");
      if (savedApplication) {
        setStep("contacts");
        setEnrichment({ status: "FAILED" });
      } else {
        setStep("capture");
      }
    }
    finally { setSaving(false); }
  };

  const selectContact = async (contact: Contact) => {
    const contactId = contact.id ?? contact.contactId;
    if (!contactId) { setError("This contact has no stable identifier."); return; }
    if (!contact.email) { setError("This contact has no verified email address."); return; }
    if (contact.verificationStatus !== "VERIFIED") { setError("This contact still needs provider verification before drafting."); return; }
    setSaving(true); setError("");
    try {
      const response = await fetch(`/api/applications/${encodeURIComponent(application?.id ?? "")}/contacts/${encodeURIComponent(contactId)}/select`, { method: "POST", headers: { "content-type": "application/json", "x-idempotency-key": `select-contact:${contactId}` }, body: JSON.stringify({}) });
      const body = await response.json().catch(() => ({})) as Contact & { error?: { message?: string } };
      if (!response.ok) throw new Error(body.error?.message ?? "That contact could not be selected.");
      const selected = { ...contact, ...body };
      setSelectedContact(selected);
      const resumeResponse = await fetch("/api/resumes/versions", { cache: "no-store" });
      const resumeBody = await resumeResponse.json().catch(() => ({})) as { items?: ResumeVersion[]; versions?: ResumeVersion[]; error?: { message?: string } };
      if (!resumeResponse.ok) throw new Error(resumeBody.error?.message ?? "Resume versions are unavailable.");
      setResumes(resumeBody.items ?? resumeBody.versions ?? []); setStep("draft");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "That contact could not be selected."); }
    finally { setSaving(false); }
  };

  const generateDraft = async () => {
    const contactId = selectedContact?.id ?? selectedContact?.contactId;
    if (!application || !selectedContact || !contactId || !resumeId) { setError("Select a verified contact and resume version before drafting."); return; }
    setSaving(true); setError("");
    try {
      const response = await fetch("/api/drafts", { method: "POST", headers: { "content-type": "application/json", "x-idempotency-key": `draft:${application.id}:${contactId}:${resumeId}` }, body: JSON.stringify({ applicationId: application.id, contactId, resumeVersionId: resumeId, recipient: selectedContact.email, contactName: selectedContact.name, company: selectedContact.company || application.company || company, role: selectedContact.role || application.role || role, instructions: instructions.trim(), draftType: "outreach", userInstructions: instructions.trim() }) });
      const body = await response.json().catch(() => ({})) as DraftResponse;
      const normalized = normalizeDraft(body);
      if (!response.ok || !normalized) throw new Error(body.error?.message ?? "A grounded draft could not be generated.");
      setDraft(normalized); notify("Draft created and kept unsent.");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "A grounded draft could not be generated."); }
    finally { setSaving(false); }
  };

  const saveDraft = async (persist: boolean) => {
    if (!draft) return;
    setSaving(true); setError("");
    try {
      const response = await fetch(`/api/drafts/${encodeURIComponent(draft.id)}`, { method: "PATCH", headers: { "content-type": "application/json" }, body: JSON.stringify({ recipient: draft.recipient, subject: draft.subject, body: draft.body, sent: false }) });
      const body = await response.json().catch(() => ({})) as DraftResponse;
      if (!response.ok) throw new Error(body.error?.message ?? "Draft changes could not be saved.");
      setDraft((current) => current ? { ...current, ...normalizeDraft(body), sent: false } : current);
      if (persist) {
        const gmailResponse = await fetch(`/api/drafts/${encodeURIComponent(draft.id)}/persist-to-gmail`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({}) });
        const gmailBody = await gmailResponse.json().catch(() => ({})) as DraftResponse;
        if (!gmailResponse.ok) throw new Error(gmailBody.error?.message ?? "The Gmail draft could not be saved.");
        setDraft((current) => current ? { ...current, ...normalizeDraft(gmailBody), sent: false } : current); notify("Saved as an unsent Gmail draft.");
      } else notify("Draft changes saved.");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Draft changes could not be saved."); }
    finally { setSaving(false); }
  };

  if (!open) return null;
  const progressLabel = enrichment?.status === "FAILED" || enrichment?.status === "PROVIDER_UNAVAILABLE" ? "Contact discovery failed" : enrichment?.status === "NO_VERIFIED_CONTACT" ? "No verified contact found" : enrichment?.status === "READY" || enrichment?.status === "COMPLETED" || enrichment?.status === "CONTACTS_READY" ? "Contact discovery complete" : "Finding sourced contacts…";
  return <div className="overlay" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) close(); }}><section className="modal capture-workflow-modal" role="dialog" aria-modal="true" aria-labelledby="capture-heading">
    <div className="modal-head"><div><span className="section-overline">Capture → contact → draft</span><h2 id="capture-heading">{step === "capture" ? "Capture a role" : step === "draft" ? "Prepare an outreach draft" : "Find the right person"}</h2><p className="section-caption">{step === "capture" ? "Save only the job details you can verify." : step === "draft" ? "Grounded in the selected contact, role, and resume. Always unsent." : progressLabel}</p></div><button className="close" aria-label="Close capture workflow" type="button" onClick={close}>×</button></div>
    <div className="workflow-steps" aria-label="Capture workflow progress"><span className={step === "capture" ? "active" : "complete"}>1 Capture</span><span className={step === "enrich" || step === "contacts" ? "active" : step === "draft" ? "complete" : ""}>2 Contacts</span><span className={step === "draft" ? "active" : ""}>3 Draft</span></div>
    {error && <div className="state-banner error workflow-error" role="alert">{error}</div>}
    {step === "capture" && <div className="modal-body"><label className="modal-label" htmlFor="capture-url">Job URL</label><input className="draft-input" id="capture-url" value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://company.com/jobs/..." autoFocus /><label className="modal-label" htmlFor="capture-title">Role title</label><input className="draft-input" id="capture-title" value={title} onChange={(event) => { setTitle(event.target.value); if (!role) setRole(event.target.value); }} placeholder="Senior Frontend Engineer" /><label className="modal-label" htmlFor="capture-role">Role</label><input className="draft-input" id="capture-role" value={role} onChange={(event) => setRole(event.target.value)} placeholder="Frontend engineering" /><label className="modal-label" htmlFor="capture-company">Company</label><input className="draft-input" id="capture-company" value={company} onChange={(event) => setCompany(event.target.value)} placeholder="Company name" /><label className="modal-label" htmlFor="capture-location">Location</label><input className="draft-input" id="capture-location" value={location} onChange={(event) => setLocation(event.target.value)} placeholder="Remote, Bengaluru…" /><label className="modal-label" htmlFor="capture-description">Visible description <span>(optional)</span></label><textarea className="draft-area compact" id="capture-description" value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Paste only the public job description you want JobFlow to use." /><div className="warning">JobFlow will use public, attributable sources and your connected Gmail. It will not guess an address, scrape private profiles, or send mail.</div></div>}
    {step === "enrich" && <div className="modal-body workflow-loading"><div className="loading-ring" aria-hidden="true" /><strong>{progressLabel}</strong><p>We are waiting for the contact discovery service. No placeholder contacts are shown.</p></div>}
    {step === "contacts" && <div className="modal-body"><div className="workflow-context"><strong>{application?.role ?? title}</strong><span>{application?.company || company || "Company not provided"}</span><small>{application?.jobUrl || url}</small></div>{enrichment?.status === "FAILED" || enrichment?.status === "PROVIDER_UNAVAILABLE" ? <div className="empty-state"><strong>Contact discovery failed</strong><span>{error || "Contact discovery could not complete. Retry without creating another application."}</span><button className="outline-button" type="button" onClick={() => void retryEnrichment()} disabled={saving}>Retry contact discovery</button></div> : enrichment?.status === "NO_VERIFIED_CONTACT" || ((enrichment?.status === "READY" || enrichment?.status === "COMPLETED") && contacts.length === 0) ? <div className="empty-state"><strong>No verified contact found</strong><span>Nothing sourced yet meets JobFlow&apos;s verification and allowed-use policy. You can still use the captured role later.</span></div> : contacts.length === 0 ? <div className="empty-state workflow-loading"><div className="loading-ring" aria-hidden="true" /><strong>Waiting for sourced contacts…</strong><span>Only real provider or public-source results will appear here.</span></div> : <div className="contact-results" aria-label="Contact results">{contacts.map((contact) => <article className="contact-result" key={contact.id}><div className="contact-result-head"><div><strong>{contact.name || "Unnamed contact"}</strong><span>{contact.role || "Role not provided"}{contact.company ? ` · ${contact.company}` : ""}</span></div><span className={`verification-badge ${contact.verificationStatus?.toLowerCase() ?? "unknown"}`}>{contact.preselected ? "Recommended · " : ""}{contact.verificationStatus ?? "UNKNOWN"}</span></div>{contact.email && <div className="contact-email">{contact.email}</div>}{contact.profileUrl && <a href={contact.profileUrl} target="_blank" rel="noreferrer">Public profile</a>}<p className="contact-evidence">{contact.evidence || "No evidence supplied by the source."}</p><div className="contact-source">Source: {contact.sourceUrl || contact.sourceMessageId || "Not provided"}{typeof contact.confidence === "number" && ` · ${Math.round(contact.confidence * 100)}% confidence`}</div><button className="primary-button" type="button" disabled={!contact.email || contact.verificationStatus === "REJECTED" || contact.verificationStatus === "EXPIRED" || saving} onClick={() => selectContact(contact)}>{contact.email ? "Confirm this contact" : "No verified email"}</button></article>)}</div>}</div>}
    {step === "draft" && <div className="modal-body">{!draft ? <><div className="workflow-context"><strong>{selectedContact?.name || selectedContact?.email}</strong><span>{selectedContact?.email}</span><small>Selected contact · sourced and user-confirmed</small></div><label className="modal-label" htmlFor="resume-upload">Resume version</label><input id="resume-upload" type="file" accept="application/pdf,.doc,.docx" className="draft-input" disabled={resumeUploading} onChange={(event) => { const file = event.target.files?.[0]; if (file) void uploadResume(file); event.currentTarget.value = ""; }} />{resumes.length === 0 ? <div className="empty-state"><strong>No resume versions available</strong><span>Upload a private resume version above before generating a draft.</span></div> : <><select className="draft-input" id="resume-version" value={resumeId} onChange={(event) => setResumeId(event.target.value)}><option value="">Select a resume version</option>{resumes.map((resume) => <option key={resume.id} value={resume.id}>{resume.name || resume.version || resume.id}{resume.updatedAt ? ` · ${new Date(resume.updatedAt).toLocaleDateString()}` : ""}</option>)}</select><label className="modal-label" htmlFor="draft-instructions">Optional instructions</label><textarea className="draft-area compact" id="draft-instructions" value={instructions} onChange={(event) => setInstructions(event.target.value)} placeholder="Keep it concise, mention the relevant project…" /><button className="primary-button" type="button" disabled={!resumeId || saving || resumeUploading} onClick={generateDraft}>{saving ? "Preparing…" : "Generate grounded draft"}</button></>}</> : <DraftEditor draft={draft} setDraft={setDraft} saving={saving} onSave={() => saveDraft(false)} onPersist={() => saveDraft(true)} />}</div>}
    <div className="modal-foot"><button className="outline-button" type="button" onClick={close} disabled={saving}>{draft ? "Done" : "Close"}</button>{step === "capture" && <button className="primary-button" type="button" onClick={capture} disabled={saving}>{saving ? "Saving…" : "Save and find contacts"}</button>}</div>
  </section></div>;
}

function normalizeDraft(value: DraftResponse): Draft | null {
  const id = value.id ?? value.draftId;
  if (!id || !value.recipient || !value.subject || typeof value.body !== "string") return null;
  return { id, recipient: value.recipient, subject: value.subject, body: value.body, sent: false, gmailDraftId: value.gmailDraftId, evidence: value.evidence };
}

function DraftEditor({ draft, setDraft, saving, onSave, onPersist }: { draft: Draft; setDraft: (draft: Draft) => void; saving: boolean; onSave: () => void; onPersist: () => void }) {
  return <div className="draft-editor"><div className="draft-safety"><span aria-hidden="true">✓</span><span>Editable draft · never sent automatically</span></div><label className="modal-label" htmlFor="draft-recipient">Recipient</label><input className="draft-input" id="draft-recipient" value={draft.recipient} onChange={(event) => setDraft({ ...draft, recipient: event.target.value })} /><label className="modal-label" htmlFor="draft-subject">Subject</label><input className="draft-input" id="draft-subject" value={draft.subject} onChange={(event) => setDraft({ ...draft, subject: event.target.value })} /><label className="modal-label" htmlFor="draft-body">Body</label><textarea className="draft-area" id="draft-body" value={draft.body} onChange={(event) => setDraft({ ...draft, body: event.target.value })} /><div className="evidence-panel"><strong>Grounding</strong>{draft.evidence?.length ? draft.evidence.map((item, index) => <p key={`${item.source ?? "evidence"}-${index}`}>{item.text}{item.source ? ` · ${item.source}` : ""}</p>) : <p>No citation panel was returned by the draft service.</p>}</div><div className="draft-actions"><button className="outline-button" type="button" onClick={onSave} disabled={saving}>Save changes</button><button className="primary-button" type="button" onClick={onPersist} disabled={saving || draft.sent}>Save Gmail draft</button></div></div>;
}
