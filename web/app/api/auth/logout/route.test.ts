import { beforeEach, describe, expect, it, vi } from "vitest";

const revokeServerSession = vi.hoisted(() => vi.fn());
const getSessionId = vi.hoisted(() => vi.fn());
vi.mock("../../../../lib/identity-client", () => ({ revokeServerSession }));
vi.mock("../../../../lib/session", () => ({ SESSION_COOKIE: "jobflow_session_id", getSessionId }));

import { POST } from "./route";

describe("POST /api/auth/logout", () => {
  beforeEach(() => { vi.clearAllMocks(); getSessionId.mockResolvedValue("session-1"); revokeServerSession.mockResolvedValue(undefined); });

  it("revokes the server session and clears the opaque cookie", async () => {
    const response = await POST(new Request("http://localhost:3000/api/auth/logout", { method: "POST", headers: { origin: "http://localhost:3000" } }));

    expect(response.status).toBe(200);
    expect(revokeServerSession).toHaveBeenCalledWith("session-1");
    expect(response.headers.get("set-cookie")).toContain("jobflow_session_id=");
  });

  it("does not claim revocation succeeded when identity-service fails", async () => {
    revokeServerSession.mockRejectedValue(new Error("identity unavailable"));

    const response = await POST(new Request("http://localhost:3000/api/auth/logout", { method: "POST", headers: { origin: "http://localhost:3000" } }));

    expect(response.status).toBe(503);
    await expect(response.json()).resolves.toMatchObject({ error: { code: "SESSION_REVOCATION_UNAVAILABLE" }, meta: {} });
    expect(response.headers.get("set-cookie")).toContain("jobflow_session_id=");
  });
});
