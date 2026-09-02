import { readInternalServiceUrl } from "../../../../lib/service-url";
import { authRequired, forwardServiceResponse, serviceUnavailable, sessionAuthorization } from "../../../../lib/service-proxy";

export async function GET(request: Request) {
  const authorization = await sessionAuthorization(request);
  if (!authorization?.startsWith("Bearer ")) return authRequired();
  try {
    const response = await fetch(`${readInternalServiceUrl("AI_DRAFT_SERVICE_URL", "http://localhost:8086")}/api/v1/resumes/versions`, { headers: { authorization }, cache: "no-store" });
    return forwardServiceResponse(response);
  } catch { return serviceUnavailable("Resume service is not reachable"); }
}

export async function POST(request: Request) {
  const authorization = await sessionAuthorization(request);
  if (!authorization?.startsWith("Bearer ")) return authRequired();
  try {
    const response = await fetch(`${readInternalServiceUrl("AI_DRAFT_SERVICE_URL", "http://localhost:8086")}/api/v1/resumes/versions`, { method: "POST", headers: { authorization, "content-type": request.headers.get("content-type") ?? "application/octet-stream" }, body: await request.arrayBuffer(), cache: "no-store" });
    return forwardServiceResponse(response);
  } catch { return serviceUnavailable("Resume service is not reachable"); }
}
