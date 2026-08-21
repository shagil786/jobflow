import { NextResponse } from "next/server";
import { getServerSessionMetadata } from "../../../../lib/identity-client";
import { readInternalServiceUrl } from "../../../../lib/service-url";

export async function POST() {
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
    const syncResponse = await fetch(`${ingestion}/internal/v1/gmail/connections/${encodeURIComponent(status.connectionId)}/sync`, { method: "POST", headers, cache: "no-store", signal: AbortSignal.timeout(30000) });
    if (!syncResponse.ok) return NextResponse.json({ error: { code: "GMAIL_SYNC_FAILED", message: "Gmail sync could not be completed" }, meta: {} }, { status: 502 });
    return new NextResponse(await syncResponse.text(), { status: 200, headers: { "content-type": "application/json" } });
  } catch {
    return NextResponse.json({ error: { code: "GMAIL_SYNC_UNAVAILABLE", message: "Gmail sync is temporarily unavailable" }, meta: {} }, { status: 503 });
  }
}
