export const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

export type GmailOAuthConfig = { clientId: string; clientSecret: string; redirectUri: string };
export type GmailTokenResponse = { accessToken: string; refreshToken: string; expiresIn: number; idToken: string };

export function readGmailOAuthConfig(env: Record<string, string | undefined>): GmailOAuthConfig {
  const clientId = env.GMAIL_CLIENT_ID; const clientSecret = env.GMAIL_CLIENT_SECRET; const redirectUri = env.GMAIL_REDIRECT_URI;
  if (!clientId) throw new Error("GMAIL_CLIENT_ID is required");
  if (!clientSecret) throw new Error("GMAIL_CLIENT_SECRET is required");
  if (!redirectUri) throw new Error("GMAIL_REDIRECT_URI is required");
  return { clientId, clientSecret, redirectUri };
}

export function buildGmailAuthorizationUrl(config: GmailOAuthConfig, state: string, challenge: string, nonce: string): string {
  const url = new URL("https://accounts.google.com/o/oauth2/v2/auth");
  url.searchParams.set("response_type", "code"); url.searchParams.set("client_id", config.clientId);
  url.searchParams.set("redirect_uri", config.redirectUri); url.searchParams.set("scope", `openid email ${GMAIL_READONLY_SCOPE}`);
  url.searchParams.set("access_type", "offline"); url.searchParams.set("prompt", "consent");
  url.searchParams.set("state", state); url.searchParams.set("nonce", nonce);
  url.searchParams.set("code_challenge", challenge); url.searchParams.set("code_challenge_method", "S256");
  return url.toString();
}

export function validateGmailTokenResponse(value: { access_token?: unknown; refresh_token?: unknown; token_type?: unknown; expires_in?: unknown; id_token?: unknown }): GmailTokenResponse {
  if (typeof value.access_token !== "string" || !value.access_token) throw new Error("Gmail access token is missing");
  if (typeof value.refresh_token !== "string" || !value.refresh_token) throw new Error("Gmail refresh token is missing");
  if (typeof value.token_type !== "string" || value.token_type.toLowerCase() !== "bearer") throw new Error("Gmail token type must be bearer");
  if (typeof value.expires_in !== "number" || value.expires_in <= 0) throw new Error("Gmail token expiry is invalid");
  if (typeof value.id_token !== "string" || !value.id_token) throw new Error("Gmail ID token is missing");
  return { accessToken: value.access_token, refreshToken: value.refresh_token, expiresIn: value.expires_in, idToken: value.id_token };
}
