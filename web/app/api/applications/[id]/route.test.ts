import { beforeEach, describe, expect, it, vi } from "vitest";

const getSessionAccessTokenSafely = vi.hoisted(() => vi.fn());
vi.mock("../../../../lib/identity-client", () => ({ getSessionAccessTokenSafely }));

import { PATCH } from "./route";

describe("PATCH /api/applications/:id", () => {
  beforeEach(() => { vi.clearAllMocks(); getSessionAccessTokenSafely.mockResolvedValue("server-access-token"); });

  it("forwards the authenticated application update to the job service", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("{}", { status: 200 }));
    const payload = JSON.stringify({ status: "follow_up" });

    const response = await PATCH(new Request("http://localhost:3000/api/applications/app-1", { method: "PATCH", body: payload }), { params: Promise.resolve({ id: "app-1" }) });

    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8091/api/v1/applications/app-1", expect.objectContaining({
      method: "PATCH", body: payload, headers: { "content-type": "application/json", authorization: "Bearer server-access-token" },
    }));
  });
});
