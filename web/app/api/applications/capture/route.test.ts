import { beforeEach, describe, expect, it, vi } from "vitest";

const getSessionAccessTokenSafely = vi.hoisted(() => vi.fn());
vi.mock("../../../../lib/identity-client", () => ({ getSessionAccessTokenSafely }));

import { POST } from "./route";

describe("POST /api/applications/capture", () => {
  beforeEach(() => { vi.clearAllMocks(); getSessionAccessTokenSafely.mockResolvedValue("server-access-token"); });

  it("forwards capture data with the caller idempotency key", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("{}", { status: 201 }));
    const payload = JSON.stringify({ url: "https://example.com/jobs/1", company: "Example", role: "Engineer" });

    const response = await POST(new Request("http://localhost:3000/api/applications/capture", {
      method: "POST", body: payload, headers: { "x-idempotency-key": "capture-1", authorization: "Bearer ignored" },
    }));

    expect(response.status).toBe(201);
    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8091/api/v1/applications/capture", expect.objectContaining({
      method: "POST", body: payload,
      headers: expect.objectContaining({ authorization: "Bearer server-access-token", "X-Idempotency-Key": "capture-1" }),
    }));
  });
});
