import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { getServerSessionMetadata } = vi.hoisted(() => ({ getServerSessionMetadata: vi.fn() }));
vi.mock("../../../../lib/identity-client", () => ({ getServerSessionMetadata }));

describe("POST /api/reviews/:suggestionId", () => {
  beforeEach(() => { vi.resetAllMocks(); vi.stubEnv("JOBFLOW_INTERNAL_SERVICE_KEY", "internal-test-key"); });

  it("requires a verified session", async () => {
    getServerSessionMetadata.mockResolvedValue(null);
    const response = await POST(new Request("http://localhost/api/reviews/s-1", { method: "POST", body: "{}" }), { params: Promise.resolve({ suggestionId: "s-1" }) });
    expect(response.status).toBe(401);
  });

  it("persists a review without allowing the browser to set ownership", async () => {
    getServerSessionMetadata.mockResolvedValue({ tenantId: "tenant-1", userId: "user-1" });
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("{}", { status: 201 }));
    const response = await POST(new Request("http://localhost/api/reviews/s-1", { method: "POST", body: JSON.stringify({ decision: "ACCEPT" }) }), { params: Promise.resolve({ suggestionId: "s-1" }) });
    expect(response.status).toBe(201);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8082/internal/v1/classification-suggestions/s-1/review?tenantId=tenant-1&userId=user-1",
      expect.objectContaining({ method: "POST", body: JSON.stringify({ decision: "ACCEPT" }) }),
    );
  });
});
