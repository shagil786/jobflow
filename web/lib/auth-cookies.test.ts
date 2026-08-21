import { describe, expect, it } from "vitest";
import { OIDC_COOKIE_NAMES, clearOidcCookies } from "./auth-cookies";

describe("OIDC callback cookies", () => {
  it("clears state, verifier, and nonce after every callback outcome", () => {
    const headers = new Headers();
    clearOidcCookies(headers);
    const setCookie = headers.get("set-cookie") ?? "";
    for (const name of OIDC_COOKIE_NAMES) expect(setCookie).toContain(`${name}=;`);
  });
});
