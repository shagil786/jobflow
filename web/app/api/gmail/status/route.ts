import { NextResponse } from "next/server";
import { getServerSessionMetadata } from "../../../../lib/identity-client";
import { readInternalServiceUrl } from "../../../../lib/service-url";

export async function GET() {
  const identity = await getServerSessionMetadata();
  if (!identity) return NextResponse.json({ error: { code: "AUTH_REQUIRED", message: "A verified JobFlow session is required" }, meta: {} }, { status: 401 });
  const key = process.env.JOBFLOW_INTERNAL_SERVICE_KEY;
  if (!key) return NextResponse.json({ error: { code: "CONFIGURATION_ERROR", message: "Ingestion service is not configured" }, meta: {} }, { status: 503 });
  try {
    const ingestionServiceUrl = readInternalServiceUrl("INGESTION_SERVICE_URL", "http://localhost:8082");
    const query = new URLSearchParams({ tenantId: identity.tenantId, userId: identity.userId });
    const response = await fetch(`${ingestionServiceUrl}/internal/v1/gmail/connections/status?${query}`, { headers: { "X-Internal-Service-Key": key }, cache: "no-store", signal: AbortSignal.timeout(5000) });
    if (!response.ok) return NextResponse.json({ error: { code: "INGESTION_UNAVAILABLE", message: "Gmail connection status is unavailable" }, meta: {} }, { status: 503 });
    return new NextResponse(await response.text(), { status: 200, headers: { "content-type": "application/json" } });
  } catch {
    return NextResponse.json({ error: { code: "INGESTION_UNAVAILABLE", message: "Gmail connection status is unavailable" }, meta: {} }, { status: 503 });
  }
}
