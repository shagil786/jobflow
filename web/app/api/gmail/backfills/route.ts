import { NextResponse } from "next/server";
import { getServerSessionMetadata } from "../../../../lib/identity-client";
import { readInternalServiceUrl } from "../../../../lib/service-url";

export async function GET(request: Request) { return proxy("GET", request); }
export async function POST(request: Request) { return proxy("POST", request); }

async function proxy(method: "GET" | "POST", request: Request) {
  const identity = await getServerSessionMetadata();
  if (!identity) return error("AUTH_REQUIRED", "A verified JobFlow session is required", 401);
  const key = process.env.JOBFLOW_INTERNAL_SERVICE_KEY;
  if (!key) return error("CONFIGURATION_ERROR", "Ingestion service is not configured", 503);
  try {
    const incoming = new URL(request.url);
    const query = new URLSearchParams({ tenantId: identity.tenantId, userId: identity.userId });
    const headers: Record<string, string> = { "X-Internal-Service-Key": key };
    if (method === "GET") incoming.searchParams.forEach((value, name) => query.set(name, value));
    if (method === "POST") {
      const idempotencyKey = request.headers.get("idempotency-key");
      if (!idempotencyKey) return error("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required", 400);
      headers["Idempotency-Key"] = idempotencyKey;
      const correlationId = request.headers.get("x-correlation-id");
      if (correlationId) headers["X-Correlation-Id"] = correlationId;
      headers["content-type"] = "application/json";
    }
    const response = await fetch(`${readInternalServiceUrl("INGESTION_SERVICE_URL", "http://localhost:8082")}/internal/v1/gmail/backfills?${query}`, {
      method, headers, body: method === "POST" ? JSON.stringify({ ...(await safeJson(request)), tenantId: identity.tenantId, userId: identity.userId }) : undefined,
      cache: "no-store", signal: AbortSignal.timeout(5000),
    });
    const location = response.headers.get("location");
    const outputHeaders = new Headers({ "content-type": response.headers.get("content-type") ?? "application/json" });
    if (location) outputHeaders.set("location", location.replace(/^\/internal\/v1\/gmail/, "/api/gmail"));
    return new NextResponse(response.body, { status: response.status, headers: outputHeaders });
  } catch { return error("INGESTION_UNAVAILABLE", "Gmail backfill service is unavailable", 503); }
}
async function safeJson(request: Request): Promise<Record<string, unknown>> { try { const body = await request.json(); return body && typeof body === "object" ? body as Record<string, unknown> : {}; } catch { return {}; } }
function error(code: string, message: string, status: number) { return NextResponse.json({ error: { code, message }, meta: {} }, { status }); }
