import { afterEach, describe, expect, it, vi } from "vitest";

const { getServerSessionMetadata } = vi.hoisted(() => ({ getServerSessionMetadata: vi.fn() }));
vi.mock("../../../../lib/identity-client", () => ({ getServerSessionMetadata }));

import { POST } from "./route";

describe("POST /api/gmail/sync", () => {
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllEnvs(); getServerSessionMetadata.mockReset(); });

  it("requires a verified session", async () => {
    getServerSessionMetadata.mockResolvedValue(null);
    expect((await POST()).status).toBe(401);
  });

  it("does not start sync when Gmail is disconnected", async () => {
    getServerSessionMetadata.mockResolvedValue({ userId: "user-1", tenantId: "tenant-1" });
    vi.stubEnv("JOBFLOW_INTERNAL_SERVICE_KEY", "internal-test-key");
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ connected: false }), { status: 200 }));
    const response = await POST();
    expect(response.status).toBe(409);
    expect(await response.json()).toMatchObject({ error: { code: "GMAIL_NOT_CONNECTED" } });
  });

  it("forwards a connected account to the internal sync endpoint", async () => {
    getServerSessionMetadata.mockResolvedValue({ userId: "user-1", tenantId: "tenant-1" });
    vi.stubEnv("JOBFLOW_INTERNAL_SERVICE_KEY", "internal-test-key");
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ connected: true, connectionId: "connection-1" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ imported: 3, fullResync: false }), { status: 200 }));
    const response = await POST();
    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenNthCalledWith(2, "http://localhost:8082/internal/v1/gmail/connections/connection-1/sync", expect.objectContaining({ method: "POST", headers: { "X-Internal-Service-Key": "internal-test-key" } }));
    expect(await response.json()).toMatchObject({ imported: 3 });
  });
});
