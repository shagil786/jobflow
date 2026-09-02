import { readInternalServiceUrl } from "../../../../../lib/service-url";
import { authRequired, forwardServiceResponse, serviceUnavailable, sessionAuthorization } from "../../../../../lib/service-proxy";

async function proxy(request: Request, context: { params: Promise<{ id: string }> }, method: "GET" | "POST") {
  const authorization = await sessionAuthorization(request);
  if (!authorization?.startsWith("Bearer ")) return authRequired();
  try {
    const { id } = await context.params;
    const response = await fetch(`${readInternalServiceUrl("CONTACT_DISCOVERY_SERVICE_URL", "http://localhost:8093")}/api/v1/applications/${encodeURIComponent(id)}/enrichment`, {
      method, headers: { "content-type": "application/json", authorization, "X-Idempotency-Key": request.headers.get("x-idempotency-key") ?? crypto.randomUUID() }, body: method === "POST" ? await request.text() : undefined, cache: "no-store",
    });
    return forwardServiceResponse(response);
  } catch { return serviceUnavailable("Contact discovery service is not reachable"); }
}

export async function POST(request: Request, context: { params: Promise<{ id: string }> }) { return proxy(request, context, "POST"); }
export async function GET(request: Request, context: { params: Promise<{ id: string }> }) { return proxy(request, context, "GET"); }
