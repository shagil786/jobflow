import { afterEach, describe, expect, it, vi } from "vitest";

const { getServerSessionMetadata } = vi.hoisted(() => ({ getServerSessionMetadata: vi.fn() }));
vi.mock("../../../../lib/identity-client", () => ({ getServerSessionMetadata }));

import { GET, POST } from "./route";

describe("/api/gmail/backfills", () => {
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllEnvs(); getServerSessionMetadata.mockReset(); });

  it("requires a verified session for reads and writes", async () => {
    getServerSessionMetadata.mockResolvedValue(null);
    expect((await GET(new Request("http://localhost/api/gmail/backfills"))).status).toBe(401);
    expect((await POST(new Request("http://localhost/api/gmail/backfills", { method: "POST", body: "{}" }))).status).toBe(401);
  });

  it("creates a backfill using server-owned identity and forwards async location", async () => {
    getServerSessionMetadata.mockResolvedValue({ tenantId: "tenant-1", userId: "user-1" });
    vi.stubEnv("JOBFLOW_INTERNAL_SERVICE_KEY", "internal-test-key");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ runId: "run-1", status: "QUEUED" }), { status: 202, headers: { location: "/internal/v1/gmail/backfills/run-1" } }));
    const response = await POST(new Request("http://localhost/api/gmail/backfills", { method: "POST", headers: { "content-type": "application/json", "idempotency-key": "idem-1", "x-correlation-id": "corr-1" }, body: JSON.stringify({ connectionId: "connection-1", tenantId: "evil-tenant", userId: "evil-user" }) }));
    expect(response.status).toBe(202);
    expect(response.headers.get("location")).toBe("/api/gmail/backfills/run-1");
    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8082/internal/v1/gmail/backfills?tenantId=tenant-1&userId=user-1", expect.objectContaining({ method: "POST", headers: expect.objectContaining({ "X-Internal-Service-Key": "internal-test-key", "Idempotency-Key": "idem-1", "X-Correlation-Id": "corr-1" }), body: JSON.stringify({ connectionId: "connection-1", tenantId: "tenant-1", userId: "user-1" }) }));
  });
});
