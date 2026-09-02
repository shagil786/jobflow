import { NextResponse } from "next/server";
import { getSessionAccessTokenSafely } from "../../../lib/identity-client";
import { serverAuthorization } from "../../../lib/session";
import { readInternalServiceUrl } from "../../../lib/service-url";

export async function GET(request: Request) {
  const authorization = serverAuthorization(await getSessionAccessTokenSafely(), request.headers.get("authorization"));
  if (!authorization?.startsWith("Bearer ")) return NextResponse.json({ error: { code: "AUTH_REQUIRED", message: "A verified JobFlow session is required" }, meta: {} }, { status: 401 });
  try {
    const response = await fetch(`${readInternalServiceUrl("JOB_SERVICE_URL", "http://localhost:8091")}/api/v1/recommendations`, { headers: { authorization }, cache: "no-store" });
    return new NextResponse(await response.text(), { status: response.status, headers: { "content-type": "application/json" } });
  } catch {
    return NextResponse.json({ error: { code: "SERVICE_UNAVAILABLE", message: "Recommendation service is not reachable" }, meta: {} }, { status: 503 });
  }
}
