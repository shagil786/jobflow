import { NextResponse } from "next/server";
import { getServerSessionMetadata } from "../../../../../../lib/identity-client";
import { readInternalServiceUrl } from "../../../../../../lib/service-url";

export async function GET(request: Request, context: { params: Promise<{ messageId: string }> }) {
  const identity = await getServerSessionMetadata();
  if (!identity) return error("AUTH_REQUIRED", "A verified JobFlow session is required", 401);
  const key = process.env.JOBFLOW_INTERNAL_SERVICE_KEY;
  if (!key) return error("CONFIGURATION_ERROR", "Ingestion service is not configured", 503);
  const { messageId } = await context.params;
  if (!messageId?.trim()) return error("MESSAGE_ID_INVALID", "A Gmail message is required", 400);
  const query = new URLSearchParams({ tenantId: identity.tenantId, userId: identity.userId });
  try {
    const response = await fetch(`${readInternalServiceUrl("INGESTION_SERVICE_URL", "http://localhost:8082")}/internal/v1/gmail/messages/${encodeURIComponent(messageId)}/thread?${query}`, { headers: { "X-Internal-Service-Key": key }, cache: "no-store", signal: AbortSignal.timeout(10000) });
    const text = await response.text();
    if (!response.ok) {
      const providerError = JSON.parse(text) as { code?: string; message?: string; error?: { code?: string; message?: string } };
      const code = providerError.error?.code ?? providerError.code ?? `GMAIL_THREAD_${response.status}`;
      const message = providerError.error?.message ?? providerError.message ?? "The Gmail thread could not be loaded";
      console.warn(`[jobflow-gmail-thread] status=${response.status} code=${code}`);
      return NextResponse.json({ error: { code, message }, meta: {} }, { status: response.status, headers: { "cache-control": "no-store" } });
    }
    return new NextResponse(text, { status: response.status, headers: { "content-type": response.headers.get("content-type") ?? "application/json", "cache-control": "no-store" } });
  } catch { return error("INGESTION_UNAVAILABLE", "The Gmail thread could not be loaded", 503); }
}

function error(code: string, message: string, status: number) { return NextResponse.json({ error: { code, message }, meta: {} }, { status }); }
