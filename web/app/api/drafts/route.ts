import { readInternalServiceUrl } from "../../../lib/service-url";
import { authRequired, forwardServiceResponse, serviceUnavailable, sessionAuthorization } from "../../../lib/service-proxy";

export async function POST(request: Request) {
  const authorization = await sessionAuthorization(request);
  if (!authorization?.startsWith("Bearer ")) return authRequired();
  try {
    const response = await fetch(`${readInternalServiceUrl("AI_DRAFT_SERVICE_URL", "http://localhost:8086")}/api/v1/drafts`, { method: "POST", headers: { "content-type": "application/json", authorization, "Idempotency-Key": request.headers.get("x-idempotency-key") ?? crypto.randomUUID() }, body: await request.text(), cache: "no-store" });
    return forwardServiceResponse(response);
  } catch { return serviceUnavailable("Draft service is not reachable"); }
}
