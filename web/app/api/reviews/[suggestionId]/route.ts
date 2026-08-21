import { NextResponse } from "next/server";
import { getServerSessionMetadata } from "../../../../lib/identity-client";
import { readInternalServiceUrl } from "../../../../lib/service-url";

export async function POST(request: Request, context: { params: Promise<{ suggestionId: string }> }) {
  const identity = await getServerSessionMetadata();
  if (!identity) return NextResponse.json({ error: { code: "AUTH_REQUIRED", message: "A verified JobFlow session is required" }, meta: {} }, { status: 401 });
  const key = process.env.JOBFLOW_INTERNAL_SERVICE_KEY;
  if (!key) return NextResponse.json({ error: { code: "CONFIGURATION_ERROR", message: "Ingestion service is not configured" }, meta: {} }, { status: 503 });
  try {
    const { suggestionId } = await context.params;
    const body = await request.text();
    const query = new URLSearchParams({ tenantId: identity.tenantId, userId: identity.userId });
    const response = await fetch(`${readInternalServiceUrl("INGESTION_SERVICE_URL", "http://localhost:8082")}/internal/v1/classification-suggestions/${encodeURIComponent(suggestionId)}/review?${query}`, { method: "POST", headers: { "content-type": "application/json", "X-Internal-Service-Key": key }, body, cache: "no-store", signal: AbortSignal.timeout(5000) });
    if (!response.ok) return NextResponse.json({ error: { code: response.status === 409 ? "REVIEW_ALREADY_COMPLETE" : "INGESTION_UNAVAILABLE", message: response.status === 409 ? "This suggestion was already reviewed" : "Review could not be saved" }, meta: {} }, { status: response.status === 409 ? 409 : 503 });
    return new NextResponse(await response.text(), { status: 201, headers: { "content-type": "application/json" } });
  } catch {
    return NextResponse.json({ error: { code: "INGESTION_UNAVAILABLE", message: "Review could not be saved" }, meta: {} }, { status: 503 });
  }
}
