import { readInternalServiceUrl } from "../../../../../lib/service-url";
import { authRequired, forwardServiceResponse, serviceUnavailable, sessionAuthorization } from "../../../../../lib/service-proxy";

export async function POST(request: Request, context: { params: Promise<{ draftId: string }> }) {
  const authorization = await sessionAuthorization(request);
  if (!authorization?.startsWith("Bearer ")) return authRequired();
  try {
    const { draftId } = await context.params;
    const response = await fetch(`${readInternalServiceUrl("AI_DRAFT_SERVICE_URL", "http://localhost:8086")}/api/v1/drafts/${encodeURIComponent(draftId)}/persist-to-gmail`, { method: "POST", headers: { "content-type": "application/json", authorization, "Idempotency-Key": request.headers.get("x-idempotency-key") ?? crypto.randomUUID() }, body: await request.text(), cache: "no-store" });
    return forwardServiceResponse(response);
  } catch { return serviceUnavailable("Draft service is not reachable"); }
}
