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

  it("starts an automatic background backfill for a connected account", async () => {
    getServerSessionMetadata.mockResolvedValue({ userId: "user-1", tenantId: "tenant-1" });
    vi.stubEnv("JOBFLOW_INTERNAL_SERVICE_KEY", "internal-test-key");
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ connected: true, connectionId: "connection-1" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ runId: "run-1", status: "QUEUED" }), { status: 202 }));
    const response = await POST();
    expect(response.status).toBe(202);
    expect(fetchMock).toHaveBeenNthCalledWith(2, expect.stringContaining("/internal/v1/gmail/backfills?tenantId=tenant-1&userId=user-1"), expect.objectContaining({ method: "POST", headers: expect.objectContaining({ "X-Internal-Service-Key": "internal-test-key", "Idempotency-Key": expect.stringContaining("dashboard-sync:connection-1:") }), body: JSON.stringify({ connectionId: "connection-1", mode: "AUTOMATIC" }) }));
    expect(await response.json()).toMatchObject({ started: true, runId: "run-1" });
  });

  it("does not require the legacy JobFlow label", async () => {
    getServerSessionMetadata.mockResolvedValue({ userId: "user-1", tenantId: "tenant-1" });
    vi.stubEnv("JOBFLOW_INTERNAL_SERVICE_KEY", "internal-test-key");
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ connected: true, connectionId: "connection-1" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ runId: "run-2", status: "QUEUED" }), { status: 202 }));
    const response = await POST();
    expect(response.status).toBe(202);
    expect(await response.json()).toMatchObject({ started: true, runId: "run-2" });
  });
});
