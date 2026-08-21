import { describe, expect, it } from "vitest";
import { buildGmailAuthorizationUrl, readGmailOAuthConfig, validateGmailTokenResponse } from "./gmail-oauth";

describe("Gmail OAuth", () => {
  const config = readGmailOAuthConfig({ GMAIL_CLIENT_ID: "client", GMAIL_CLIENT_SECRET: "secret", GMAIL_REDIRECT_URI: "http://localhost:3000/api/gmail/callback" });

  it("requests only readonly Gmail access with state and PKCE", () => {
    const url = new URL(buildGmailAuthorizationUrl(config, "state-1", "challenge-1", "nonce-1"));
    expect(url.searchParams.get("scope")).toContain("https://www.googleapis.com/auth/gmail.readonly");
    expect(url.searchParams.get("scope")).not.toContain("gmail.modify");
    expect(url.searchParams.get("state")).toBe("state-1");
    expect(url.searchParams.get("code_challenge")).toBe("challenge-1");
    expect(url.searchParams.get("nonce")).toBe("nonce-1");
  });

  it("requires a refresh token and bearer access token", () => {
    expect(validateGmailTokenResponse({ access_token: "a", refresh_token: "r", token_type: "Bearer", expires_in: 3600, id_token: "id" })).toEqual({ accessToken: "a", refreshToken: "r", expiresIn: 3600, idToken: "id" });
    expect(() => validateGmailTokenResponse({ access_token: "a", token_type: "Bearer", expires_in: 3600 })).toThrow();
  });
});
