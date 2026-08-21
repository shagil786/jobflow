import { NextResponse } from "next/server";
import { getServerSessionMetadata } from "../../../../lib/identity-client";
import { readInternalServiceUrl } from "../../../../lib/service-url";

export async function POST(request: Request, context: { params: Promise<{ runId: string }> }) {
  const identity = await getServerSessionMetadata();
  if (!identity) return error("AUTH_REQUIRED", "A verified JobFlow session is required", 401);
  const key = process.env.JOBFLOW_INTERNAL_SERVICE_KEY;
  if (!key) return error("CONFIGURATION_ERROR", "Ingestion service is not configured", 503);
  try {
    const { runId } = await context.params;
    const action = new URL(request.url).pathname.split("/").at(-1);
    const query = new URLSearchParams({ tenantId: identity.tenantId, userId: identity.userId });
    const response = await fetch(`${readInternalServiceUrl("INGESTION_SERVICE_URL", "http://localhost:8082")}/internal/v1/gmail/backfills/${encodeURIComponent(runId)}/${action}?${query}`, { method: "POST", headers: { "X-Internal-Service-Key": key, "content-type": "application/json" }, body: JSON.stringify(await safeJson(request)), cache: "no-store", signal: AbortSignal.timeout(5000) });
    return new NextResponse(response.body, { status: response.status, headers: { "content-type": response.headers.get("content-type") ?? "application/json" } });
  } catch { return error("INGESTION_UNAVAILABLE", "Gmail backfill service is unavailable", 503); }
}
async function safeJson(request: Request): Promise<Record<string, unknown>> { try { const body = await request.json(); return body && typeof body === "object" ? body as Record<string, unknown> : {}; } catch { return {}; } }
function error(code: string, message: string, status: number) { return NextResponse.json({ error: { code, message }, meta: {} }, { status }); }
