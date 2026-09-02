import { beforeEach, describe, expect, it, vi } from "vitest";

const getSessionAccessTokenSafely = vi.hoisted(() => vi.fn());
vi.mock("../../../lib/identity-client", () => ({ getSessionAccessTokenSafely }));

import { GET } from "./route";

describe("GET /api/recommendations", () => {
  beforeEach(() => { vi.clearAllMocks(); getSessionAccessTokenSafely.mockResolvedValue("server-access-token"); });

  it("forwards the authenticated request and preserves the honest no-feed response", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ status: "NO_FEED_CONFIGURED", items: [] }), { status: 200 }));
    const response = await GET(new Request("http://localhost:3000/api/recommendations"));

    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8091/api/v1/recommendations", expect.objectContaining({ headers: { authorization: "Bearer server-access-token" } }));
    expect(await response.json()).toEqual({ status: "NO_FEED_CONFIGURED", items: [] });
  });
});
