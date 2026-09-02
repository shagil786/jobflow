import { NextResponse } from "next/server";
import { getServerSessionMetadata } from "../../../../lib/identity-client";
import { readInternalServiceUrl } from "../../../../lib/service-url";

export async function POST(request: Request) {
  const identity = await getServerSessionMetadata();
  if (!identity) return NextResponse.json({ error: { code: "AUTH_REQUIRED", message: "A verified JobFlow session is required" }, meta: {} }, { status: 401 });
  const key = process.env.JOBFLOW_INTERNAL_SERVICE_KEY;
  if (!key) return NextResponse.json({ error: { code: "CONFIGURATION_ERROR", message: "Ingestion service is not configured" }, meta: {} }, { status: 503 });
  try {
    const ingestion = readInternalServiceUrl("INGESTION_SERVICE_URL", "http://localhost:8082");
    const headers = { "X-Internal-Service-Key": key };
    const query = new URLSearchParams({ tenantId: identity.tenantId, userId: identity.userId });
    const statusResponse = await fetch(`${ingestion}/internal/v1/gmail/connections/status?${query}`, { headers, cache: "no-store", signal: AbortSignal.timeout(5000) });
    if (!statusResponse.ok) return NextResponse.json({ error: { code: "INGESTION_UNAVAILABLE", message: "Gmail connection status is unavailable" }, meta: {} }, { status: 503 });
    const status = await statusResponse.json() as { connectionId?: string; connected?: boolean };
    if (!status.connected || !status.connectionId) return NextResponse.json({ error: { code: "GMAIL_NOT_CONNECTED", message: "Connect Gmail before syncing" }, meta: {} }, { status: 409 });
    const requested = await request.json().catch(() => ({})) as { mode?: "FOCUSED" | "BROAD" | "FULL" };
    const mode = requested.mode === "BROAD" || requested.mode === "FULL" ? requested.mode : "FOCUSED";
    const backfillResponse = await fetch(`${ingestion}/internal/v1/gmail/backfills?${query}`, {
      method: "POST",
      headers: { ...headers, "Idempotency-Key": `dashboard-sync:${status.connectionId}:${new Date().toISOString().slice(0, 10)}`, "content-type": "application/json" },
      body: JSON.stringify({ connectionId: status.connectionId, mode }),
      cache: "no-store",
      signal: AbortSignal.timeout(10000),
    });
    if (!backfillResponse.ok) {
      const upstream = await backfillResponse.json().catch(() => null) as { code?: string; message?: string; error?: { code?: string; message?: string } } | null;
      const upstreamCode = upstream?.error?.code ?? upstream?.code;
      const upstreamMessage = upstream?.error?.message ?? upstream?.message;
      console.info(`[jobflow-gmail-sync] backfill-start status=${backfillResponse.status} code=${upstreamCode ?? "UNKNOWN"}`);
      if (backfillResponse.status === 409 && upstreamCode === "GMAIL_BACKFILL_ALREADY_ACTIVE") {
        return NextResponse.json({ error: { code: "GMAIL_SYNC_ALREADY_RUNNING", message: "Gmail is already scanning in the background" }, meta: {} }, { status: 409 });
      }
      if (backfillResponse.status === 409 && upstreamCode === "GMAIL_BACKFILL_TERMINAL") {
        return NextResponse.json({ error: { code: "GMAIL_SYNC_STALE_RUN", message: "The previous Gmail scan is stale. Start a new scan." }, meta: {} }, { status: 409 });
      }
      return NextResponse.json({ error: { code: "GMAIL_SYNC_FAILED", message: "Gmail scan could not be started" }, meta: {} }, { status: 502 });
    }
    return new NextResponse(JSON.stringify({ started: true, ...(await backfillResponse.json()) }), { status: 202, headers: { "content-type": "application/json" } });
  } catch {
    return NextResponse.json({ error: { code: "GMAIL_SYNC_UNAVAILABLE", message: "Gmail sync is temporarily unavailable" }, meta: {} }, { status: 503 });
  }
}
