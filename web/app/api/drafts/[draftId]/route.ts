import { readInternalServiceUrl } from "../../../../lib/service-url";
import { authRequired, forwardServiceResponse, serviceUnavailable, sessionAuthorization } from "../../../../lib/service-proxy";

async function proxy(request: Request, context: { params: Promise<{ draftId: string }> }, method: "GET" | "PATCH") {
  const authorization = await sessionAuthorization(request);
  if (!authorization?.startsWith("Bearer ")) return authRequired();
  try {
    const { draftId } = await context.params;
    const response = await fetch(`${readInternalServiceUrl("AI_DRAFT_SERVICE_URL", "http://localhost:8086")}/api/v1/drafts/${encodeURIComponent(draftId)}`, { method, headers: { "content-type": "application/json", authorization }, body: method === "PATCH" ? await request.text() : undefined, cache: "no-store" });
    return forwardServiceResponse(response);
  } catch { return serviceUnavailable("Draft service is not reachable"); }
}

export async function GET(request: Request, context: { params: Promise<{ draftId: string }> }) { return proxy(request, context, "GET"); }
export async function PATCH(request: Request, context: { params: Promise<{ draftId: string }> }) { return proxy(request, context, "PATCH"); }
