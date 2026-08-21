import { NextResponse } from "next/server";
import { buildGmailAuthorizationUrl, readGmailOAuthConfig } from "../../../../lib/gmail-oauth";
import { createCodeChallenge, randomUrlToken } from "../../../../lib/oidc";
import { getServerSessionMetadata } from "../../../../lib/identity-client";

export async function GET(request: Request) {
  if (!await getServerSessionMetadata()) return NextResponse.redirect(new URL("/", request.url));
  try {
    const config = readGmailOAuthConfig({ GMAIL_CLIENT_ID: process.env.GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET: process.env.GMAIL_CLIENT_SECRET, GMAIL_REDIRECT_URI: process.env.GMAIL_REDIRECT_URI });
    const state = randomUrlToken(); const nonce = randomUrlToken(); const verifier = randomUrlToken(48); const challenge = await createCodeChallenge(verifier);
    const response = NextResponse.redirect(buildGmailAuthorizationUrl(config, state, challenge, nonce));
    const options = { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax" as const, maxAge: 600, path: "/" };
    response.cookies.set("jobflow_gmail_state", state, options); response.cookies.set("jobflow_gmail_verifier", verifier, options); response.cookies.set("jobflow_gmail_nonce", nonce, options);
    return response;
  } catch { return NextResponse.json({ error: { code: "GMAIL_OAUTH_UNAVAILABLE", message: "Gmail OAuth is not configured" }, meta: {} }, { status: 503 }); }
}
