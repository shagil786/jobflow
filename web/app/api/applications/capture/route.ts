import { NextResponse } from "next/server";
import { createHash } from "node:crypto";
import { getSessionAccessTokenSafely } from "../../../../lib/identity-client";
import { serverAuthorization } from "../../../../lib/session";
import { readInternalServiceUrl } from "../../../../lib/service-url";

export async function POST(request: Request) {
  const body = await request.text();
  const authorization = serverAuthorization(await getSessionAccessTokenSafely(), request.headers.get("authorization"));
  if (!authorization?.startsWith("Bearer ")) return NextResponse.json({ error: { code: "AUTH_REQUIRED", message: "A verified JobFlow session is required" }, meta: {} }, { status: 401 });
  const requestedIdempotencyKey = request.headers.get("x-idempotency-key") ?? crypto.randomUUID();
  // Job URLs can be long tracking/safety redirects. Keep the idempotency key
  // within the service schema while retaining a collision-resistant identity.
  const idempotencyKey = requestedIdempotencyKey.length <= 240
    ? requestedIdempotencyKey
    : `ui:sha256:${createHash("sha256").update(requestedIdempotencyKey).digest("hex")}`;
  try {
    const jobServiceUrl = readInternalServiceUrl("JOB_SERVICE_URL", "http://localhost:8091");
    const response = await fetch(`${jobServiceUrl}/api/v1/applications/capture`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization,
        "X-Idempotency-Key": idempotencyKey,
      },
      body,
      cache: "no-store",
    });
    const responseBody = await response.text();
    return new NextResponse(responseBody, { status: response.status, headers: { "content-type": "application/json" } });
  } catch {
    return NextResponse.json({ error: { code: "SERVICE_UNAVAILABLE", message: "Job service is not reachable" }, meta: {} }, { status: 503 });
  }
}
