import { NextResponse } from "next/server";
import { buildAuthorizationUrl, createCodeChallenge, randomUrlToken, readOidcConfig } from "../../../../lib/oidc";

export async function GET() {
  try {
    const config = readOidcConfig({ JOBFLOW_OIDC_ISSUER_URI: process.env.JOBFLOW_OIDC_ISSUER_URI, JOBFLOW_OIDC_CLIENT_ID: process.env.JOBFLOW_OIDC_CLIENT_ID, JOBFLOW_OIDC_CLIENT_SECRET: process.env.JOBFLOW_OIDC_CLIENT_SECRET, JOBFLOW_OIDC_REDIRECT_URI: process.env.JOBFLOW_OIDC_REDIRECT_URI, JOBFLOW_OIDC_AUDIENCE: process.env.JOBFLOW_OIDC_AUDIENCE });
    const discovery = await fetch(`${config.issuerUri}/.well-known/openid-configuration`, { cache: "no-store" }).then((response) => response.ok ? response.json() as Promise<{ authorization_endpoint?: string }> : Promise.reject(new Error("OIDC discovery failed")));
    if (!discovery.authorization_endpoint) return NextResponse.json({ error: { code: "OIDC_CONFIGURATION_ERROR", message: "OIDC authorization endpoint is missing" }, meta: {} }, { status: 503 });
    const state = randomUrlToken();
    const nonce = randomUrlToken();
    const verifier = randomUrlToken(48);
    const challenge = await createCodeChallenge(verifier);
    const response = NextResponse.redirect(buildAuthorizationUrl(config, state, challenge, discovery.authorization_endpoint, nonce));
    const cookieOptions = { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax" as const, maxAge: 600, path: "/" };
    response.cookies.set("jobflow_oidc_state", state, cookieOptions);
    response.cookies.set("jobflow_oidc_verifier", verifier, cookieOptions);
    response.cookies.set("jobflow_oidc_nonce", nonce, cookieOptions);
    return response;
  } catch {
    return NextResponse.json({ error: { code: "OIDC_UNAVAILABLE", message: "The identity provider is unavailable" }, meta: {} }, { status: 503 });
  }
}
