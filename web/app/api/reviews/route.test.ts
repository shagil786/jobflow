import { beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "./route";

const { getServerSessionMetadata } = vi.hoisted(() => ({ getServerSessionMetadata: vi.fn() }));
vi.mock("../../../lib/identity-client", () => ({ getServerSessionMetadata }));

describe("GET /api/reviews", () => {
  beforeEach(() => { vi.resetAllMocks(); vi.stubEnv("JOBFLOW_INTERNAL_SERVICE_KEY", "internal-test-key"); });

  it("requires a verified session", async () => {
    getServerSessionMetadata.mockResolvedValue(null);
    const response = await GET();
    expect(response.status).toBe(401);
  });

  it("forwards only the signed-in tenant and user scope", async () => {
    getServerSessionMetadata.mockResolvedValue({ tenantId: "tenant-1", userId: "user-1" });
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("[]", { status: 200, headers: { "content-type": "application/json" } }));
    const response = await GET();
    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8082/internal/v1/classification-suggestions/review-queue?tenantId=tenant-1&userId=user-1",
      expect.objectContaining({ headers: { "X-Internal-Service-Key": "internal-test-key" }, cache: "no-store" }),
    );
  });
});
