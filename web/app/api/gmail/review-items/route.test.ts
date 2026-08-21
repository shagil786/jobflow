import { beforeEach, describe, expect, it, vi } from "vitest";

const { getServerSessionMetadata } = vi.hoisted(() => ({ getServerSessionMetadata: vi.fn() }));
vi.mock("../../../../lib/identity-client", () => ({ getServerSessionMetadata }));

import { GET } from "./route";

describe("GET /api/gmail/review-items", () => {
  beforeEach(() => { vi.resetAllMocks(); vi.stubEnv("JOBFLOW_INTERNAL_SERVICE_KEY", "internal-test-key"); });

  it("requires a verified session", async () => {
    getServerSessionMetadata.mockResolvedValue(null);
    expect((await GET(new Request("http://localhost/api/gmail/review-items"))).status).toBe(401);
  });

  it("forwards cursor and server-owned scope without exposing provider identifiers", async () => {
    getServerSessionMetadata.mockResolvedValue({ tenantId: "tenant-1", userId: "user-1" });
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("{\"items\":[],\"nextCursor\":null}", { status: 200 }));
    const response = await GET(new Request("http://localhost/api/gmail/review-items?cursor=cursor-1&limit=25"));
    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8082/internal/v1/gmail/review-items?tenantId=tenant-1&userId=user-1&cursor=cursor-1&limit=25", expect.objectContaining({ headers: { "X-Internal-Service-Key": "internal-test-key" }, cache: "no-store" }));
  });
});
