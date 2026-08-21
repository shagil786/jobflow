import { describe, expect, it } from "vitest";
import { buildAuthorizationUrl, readOidcConfig, validateTokenResponse } from "./oidc";

describe("OIDC configuration", () => {
  it("rejects an incomplete provider configuration", () => {
    expect(() => readOidcConfig({ JOBFLOW_OIDC_ISSUER_URI: "https://id.example" })).toThrow("JOBFLOW_OIDC_CLIENT_ID");
  });

  it("builds an authorization request with state and PKCE", () => {
    const config = readOidcConfig({ JOBFLOW_OIDC_ISSUER_URI: "https://id.example/", JOBFLOW_OIDC_CLIENT_ID: "client-123", JOBFLOW_OIDC_CLIENT_SECRET: "secret", JOBFLOW_OIDC_REDIRECT_URI: "http://localhost:3000/api/auth/callback" });
    const url = new URL(buildAuthorizationUrl(config, "state-123", "challenge-456"));
    expect(url.origin).toBe("https://id.example");
    expect(url.searchParams.get("state")).toBe("state-123");
    expect(url.searchParams.get("code_challenge")).toBe("challenge-456");
    expect(url.searchParams.get("code_challenge_method")).toBe("S256");
    expect(url.searchParams.get("client_id")).toBe("client-123");
  });

  it("rejects a token response without a bearer token and expiry", () => {
    expect(() => validateTokenResponse({ access_token: "token", token_type: "Basic", expires_in: 3600 })).toThrow("bearer");
    expect(() => validateTokenResponse({ access_token: "token", token_type: "Bearer", expires_in: 0 })).toThrow("expiry");
  });

  it("requires an ID token for an OpenID Connect response", () => {
    expect(() => validateTokenResponse({ access_token: "token", token_type: "Bearer", expires_in: 3600 })).toThrow("ID token");
  });
});
