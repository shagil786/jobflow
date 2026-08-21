import { NextResponse } from "next/server";
import { isSameOrigin } from "../../../../lib/csrf";
import { revokeServerSession } from "../../../../lib/identity-client";
import { SESSION_COOKIE, getSessionId } from "../../../../lib/session";

export async function POST(request: Request) {
  if (!isSameOrigin(request.headers.get("origin"), new URL(request.url).origin)) return NextResponse.json({ error: { code: "CSRF_ORIGIN_REJECTED", message: "The logout request origin is not allowed" }, meta: {} }, { status: 403 });
  let status = 200;
  let body: object = { signedOut: true };
  try { await revokeServerSession(await getSessionId()); }
  catch { status = 503; body = { error: { code: "SESSION_REVOCATION_UNAVAILABLE", message: "The server session could not be revoked" }, meta: {} }; }
  const response = NextResponse.json(body, { status });
  response.cookies.delete(SESSION_COOKIE); response.headers.set("cache-control", "no-store");
  return response;
}
