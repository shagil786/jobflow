import { NextResponse } from "next/server";
import { getServerSessionMetadata } from "../../../../lib/identity-client";
import { readInternalServiceUrl } from "../../../../lib/service-url";

export async function GET(request: Request) {
  const identity = await getServerSessionMetadata();
  if (!identity) return error("AUTH_REQUIRED", "A verified JobFlow session is required", 401);
  const key = process.env.JOBFLOW_INTERNAL_SERVICE_KEY;
  if (!key) return error("CONFIGURATION_ERROR", "Ingestion service is not configured", 503);
  try {
    const incoming = new URL(request.url);
    const query = new URLSearchParams({ tenantId: identity.tenantId, userId: identity.userId });
    for (const name of ["cursor", "limit"]) { const value = incoming.searchParams.get(name); if (value) query.set(name, value); }
    const response = await fetch(`${readInternalServiceUrl("INGESTION_SERVICE_URL", "http://localhost:8082")}/internal/v1/gmail/review-items?${query}`, { headers: { "X-Internal-Service-Key": key }, cache: "no-store", signal: AbortSignal.timeout(5000) });
    const body = await response.text();
    if (response.ok) {
      const parsed = JSON.parse(body) as { items?: unknown[]; nextCursor?: string | null };
      console.info(`[jobflow-review-items] count=${parsed.items?.length ?? 0} has-next=${Boolean(parsed.nextCursor)}`);
    }
    return new NextResponse(body, { status: response.status, headers: { "content-type": response.headers.get("content-type") ?? "application/json" } });
  } catch { return error("INGESTION_UNAVAILABLE", "Review items are unavailable", 503); }
}
function error(code: string, message: string, status: number) { return NextResponse.json({ error: { code, message }, meta: {} }, { status }); }
