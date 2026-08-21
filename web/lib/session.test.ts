import { describe, expect, it } from "vitest";
import { SESSION_COOKIE, serverAuthorization } from "./session";

describe("server authorization", () => {
  it("uses an opaque server-side session cookie", () => {
    expect(SESSION_COOKIE).toBe("jobflow_session_id");
  });

  it("always uses the HttpOnly session token instead of a caller header", () => {
    expect(serverAuthorization("session-token", "Bearer caller-token")).toBe("Bearer session-token");
  });
});
