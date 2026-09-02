import { beforeEach, describe, expect, it, vi } from "vitest";

const getSessionAccessTokenSafely = vi.hoisted(() => vi.fn());
vi.mock("../../../../../lib/identity-client", () => ({ getSessionAccessTokenSafely }));

import { GET } from "./route";

describe("GET /api/applications/:id/contacts", () => {
  beforeEach(() => { vi.clearAllMocks(); getSessionAccessTokenSafely.mockResolvedValue("server-access-token"); });

  it("uses the real contact discovery service default and forwards the session", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ status: "NO_VERIFIED_CONTACT", items: [] }), { status: 200 }));
    const response = await GET(new Request("http://localhost:3000/api/applications/app-1/contacts"), { params: Promise.resolve({ id: "app-1" }) });
    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8093/api/v1/applications/app-1/contacts", expect.objectContaining({ headers: { authorization: "Bearer server-access-token" } }));
    expect(await response.json()).toMatchObject({ status: "NO_VERIFIED_CONTACT", items: [] });
  });
});
