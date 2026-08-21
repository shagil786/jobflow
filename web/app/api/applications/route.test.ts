import { afterEach, describe, expect, it, vi } from "vitest";

const { getSessionAccessTokenSafely } = vi.hoisted(() => ({ getSessionAccessTokenSafely: vi.fn() }));

vi.mock("../../../lib/identity-client", () => ({ getSessionAccessTokenSafely }));
vi.mock("../../../lib/session", () => ({
  serverAuthorization: (token: string | null) => token ? `Bearer ${token}` : null,
}));

import { GET } from "./route";

describe("GET /api/applications", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllEnvs();
    getSessionAccessTokenSafely.mockReset();
  });

  it("rejects requests without a verified server session", async () => {
    getSessionAccessTokenSafely.mockResolvedValue(null);

    const response = await GET(new Request("http://localhost/api/applications"));

    expect(response.status).toBe(401);
    expect(await response.json()).toMatchObject({ error: { code: "AUTH_REQUIRED" } });
  });

  it("forwards authenticated reads with the server-held bearer token", async () => {
    getSessionAccessTokenSafely.mockResolvedValue("access-token");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ items: [] }), { status: 200, headers: { "content-type": "application/json" } }));

    const response = await GET(new Request("http://localhost/api/applications"));

    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8091/api/v1/applications", { headers: { authorization: "Bearer access-token" }, cache: "no-store" });
    expect(await response.json()).toEqual({ items: [] });
  });
});
