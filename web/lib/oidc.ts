export interface OidcConfig {
  issuerUri: string;
  clientId: string;
  clientSecret: string;
  redirectUri: string;
  audience?: string;
}

export interface ValidatedTokenResponse {
  accessToken: string;
  expiresIn: number;
  idToken: string;
  refreshToken?: string;
}

export function readOidcConfig(env: Record<string, string | undefined>): OidcConfig {
  const issuerUri = env.JOBFLOW_OIDC_ISSUER_URI?.replace(/\/$/, "");
  const clientId = env.JOBFLOW_OIDC_CLIENT_ID;
  const clientSecret = env.JOBFLOW_OIDC_CLIENT_SECRET;
  const redirectUri = env.JOBFLOW_OIDC_REDIRECT_URI;
  const audience = env.JOBFLOW_OIDC_AUDIENCE?.trim() || undefined;
  if (!issuerUri) throw new Error("JOBFLOW_OIDC_ISSUER_URI is required");
  if (!clientId) throw new Error("JOBFLOW_OIDC_CLIENT_ID is required");
  if (!clientSecret) throw new Error("JOBFLOW_OIDC_CLIENT_SECRET is required");
  if (!redirectUri) throw new Error("JOBFLOW_OIDC_REDIRECT_URI is required");
  return { issuerUri, clientId, clientSecret, redirectUri, audience };
}

export function buildAuthorizationUrl(config: OidcConfig, state: string, codeChallenge: string, authorizationEndpoint = `${config.issuerUri}/authorize`, nonce?: string): string {
  const url = new URL(authorizationEndpoint);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("client_id", config.clientId);
  url.searchParams.set("redirect_uri", config.redirectUri);
  url.searchParams.set("scope", "openid email profile");
  if (config.audience) url.searchParams.set("audience", config.audience);
  url.searchParams.set("state", state);
  url.searchParams.set("code_challenge", codeChallenge);
  url.searchParams.set("code_challenge_method", "S256");
  if (nonce) url.searchParams.set("nonce", nonce);
  return url.toString();
}

export function validateTokenResponse(value: { access_token?: unknown; token_type?: unknown; expires_in?: unknown; id_token?: unknown; refresh_token?: unknown }): ValidatedTokenResponse {
  if (typeof value.access_token !== "string" || value.access_token.length === 0) throw new Error("OIDC access token is missing");
  if (typeof value.token_type !== "string" || value.token_type.toLowerCase() !== "bearer") throw new Error("OIDC token type must be bearer");
  if (typeof value.expires_in !== "number" || !Number.isFinite(value.expires_in) || value.expires_in <= 0) throw new Error("OIDC token expiry is invalid");
  if (typeof value.id_token !== "string" || value.id_token.length === 0) throw new Error("OIDC ID token is missing");
  return { accessToken: value.access_token, expiresIn: value.expires_in, idToken: value.id_token,
    refreshToken: typeof value.refresh_token === "string" ? value.refresh_token : undefined };
}

export function randomUrlToken(bytes = 32): string {
  const value = new Uint8Array(bytes);
  crypto.getRandomValues(value);
  return base64Url(value);
}

export async function createCodeChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

function base64Url(value: Uint8Array): string {
  let binary = "";
  value.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
