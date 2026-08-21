import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { createRemoteJWKSet, jwtVerify } from "jose";
import { createHash } from "node:crypto";
import { clearOidcCookies } from "../../../../lib/auth-cookies";
import { createServerSession } from "../../../../lib/identity-client";
import { readOidcConfig, validateTokenResponse } from "../../../../lib/oidc";
import { SESSION_COOKIE } from "../../../../lib/session";

function failure(message: string, status: number): NextResponse {
  const response = NextResponse.json({ error: { code: "OIDC_CALLBACK_FAILED", message }, meta: {} }, { status });
  clearOidcCookies(response.headers);
  response.cookies.delete("jobflow_oidc_state"); response.cookies.delete("jobflow_oidc_verifier"); response.cookies.delete("jobflow_oidc_nonce");
  return response;
}

export async function GET(request: Request) {
  const query = new URL(request.url).searchParams;
  const stored = await cookies();
  const expectedState = stored.get("jobflow_oidc_state")?.value;
  const verifier = stored.get("jobflow_oidc_verifier")?.value;
  const nonce = stored.get("jobflow_oidc_nonce")?.value;
  const code = query.get("code"); const state = query.get("state");
  const providerError = query.get("error");
  if (providerError) {
    const description = query.get("error_description")?.trim();
    const message = description ? `Identity provider rejected the sign-in: ${description}` : `Identity provider rejected the sign-in (${providerError})`;
    return failure(message, 400);
  }
  if (!code || !state || !expectedState || state !== expectedState || !verifier || !nonce) return failure("The sign-in response could not be verified", 400);
  try {
    const config = readOidcConfig({ JOBFLOW_OIDC_ISSUER_URI: process.env.JOBFLOW_OIDC_ISSUER_URI, JOBFLOW_OIDC_CLIENT_ID: process.env.JOBFLOW_OIDC_CLIENT_ID, JOBFLOW_OIDC_CLIENT_SECRET: process.env.JOBFLOW_OIDC_CLIENT_SECRET, JOBFLOW_OIDC_REDIRECT_URI: process.env.JOBFLOW_OIDC_REDIRECT_URI, JOBFLOW_OIDC_AUDIENCE: process.env.JOBFLOW_OIDC_AUDIENCE });
    const discovery = await fetch(`${config.issuerUri}/.well-known/openid-configuration`, { cache: "no-store" }).then((response) => response.ok ? response.json() as Promise<{ issuer?: string; token_endpoint?: string; jwks_uri?: string }> : Promise.reject(new Error("OIDC discovery failed")));
    if (!discovery.token_endpoint || !discovery.jwks_uri) return failure("OIDC token configuration is incomplete", 503);
    const discoveredIssuer = discovery.issuer ?? `${config.issuerUri}/`;
    const body = new URLSearchParams({ grant_type: "authorization_code", code, client_id: config.clientId, client_secret: config.clientSecret, redirect_uri: config.redirectUri, code_verifier: verifier });
    const tokenResponse = await fetch(discovery.token_endpoint, { method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" }, body, cache: "no-store" });
    const token = await tokenResponse.json() as { access_token?: unknown; token_type?: unknown; expires_in?: unknown; id_token?: unknown; refresh_token?: unknown };
    if (!tokenResponse.ok) return failure("The sign-in token could not be established", 502);
    const validated = validateTokenResponse(token);
    const verified = await jwtVerify(validated.idToken, createRemoteJWKSet(new URL(discovery.jwks_uri)) as Parameters<typeof jwtVerify>[1], { issuer: discoveredIssuer, audience: config.clientId });
    if (verified.payload.nonce !== nonce) throw new Error("OIDC nonce mismatch");
    const userId = typeof verified.payload.sub === "string" ? verified.payload.sub : null;
    const explicitTenantId = typeof verified.payload.tenant_id === "string" ? verified.payload.tenant_id : verified.payload["https://jobflow.app/tenant_id"];
    const tenantId = typeof explicitTenantId === "string" && explicitTenantId.trim() ? explicitTenantId : userId ? `personal:${createHash("sha256").update(userId).digest("hex")}` : null;
    if (!userId || !tenantId) return failure("The verified identity has no JobFlow tenant", 403);
    const session = await createServerSession({ userId, tenantId, provider: config.issuerUri, accessToken: validated.accessToken, refreshToken: validated.refreshToken, accessTokenExpiresAt: new Date(Date.now() + validated.expiresIn * 1000).toISOString() });
    const response = NextResponse.redirect(new URL("/", config.redirectUri));
    response.cookies.set(SESSION_COOKIE, session.sessionId, { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", maxAge: 60 * 60 * 24 * 30, path: "/" });
    response.cookies.delete("jobflow_oidc_state"); response.cookies.delete("jobflow_oidc_verifier"); response.cookies.delete("jobflow_oidc_nonce");
    return response;
  } catch {
    return failure("The identity provider is unavailable", 503);
  }
}
