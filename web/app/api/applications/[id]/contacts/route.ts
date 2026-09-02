import { readInternalServiceUrl } from "../../../../../lib/service-url";
import { authRequired, forwardServiceResponse, serviceUnavailable, sessionAuthorization } from "../../../../../lib/service-proxy";

export async function GET(request: Request, context: { params: Promise<{ id: string }> }) {
  const authorization = await sessionAuthorization(request);
  if (!authorization?.startsWith("Bearer ")) return authRequired();
  try {
    const { id } = await context.params;
    const response = await fetch(`${readInternalServiceUrl("CONTACT_DISCOVERY_SERVICE_URL", "http://localhost:8093")}/api/v1/applications/${encodeURIComponent(id)}/contacts`, { headers: { authorization }, cache: "no-store" });
    return forwardServiceResponse(response);
  } catch { return serviceUnavailable("Contact discovery service is not reachable"); }
}
