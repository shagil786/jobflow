export const OIDC_COOKIE_NAMES = ["jobflow_oidc_state", "jobflow_oidc_verifier", "jobflow_oidc_nonce"] as const;

export function clearOidcCookies(headers: Headers): void {
  for (const name of OIDC_COOKIE_NAMES) headers.append("set-cookie", `${name}=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax`);
}
