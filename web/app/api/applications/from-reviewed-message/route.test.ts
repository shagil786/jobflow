import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { getSessionAccessTokenSafely } = vi.hoisted(() => ({ getSessionAccessTokenSafely: vi.fn() }));
vi.mock("../../../../lib/identity-client", () => ({ getSessionAccessTokenSafely }));
vi.mock("../../../../lib/session", () => ({ serverAuthorization: (token: string | null) => token ? `Bearer ${token}` : null }));

describe("POST /api/applications/from-reviewed-message", () => {
  beforeEach(() => { vi.resetAllMocks(); getSessionAccessTokenSafely.mockResolvedValue("access-token"); });

  it("requires the server-side session", async () => {
    getSessionAccessTokenSafely.mockResolvedValue(null);
    const response = await POST(new Request("http://localhost", { method: "POST", body: "{}" }));
    expect(response.status).toBe(401);
  });

  it("forwards the reviewed-message command to the job service", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("{}", { status: 201 }));
    const body = JSON.stringify({ mode: "CREATE", reviewId: "review-1", messageId: "message-1", role: "Engineer" });
    const response = await POST(new Request("http://localhost", { method: "POST", body }));
    expect(response.status).toBe(201);
    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8091/api/v1/applications/from-reviewed-message", expect.objectContaining({ method: "POST", body, headers: { "content-type": "application/json", authorization: "Bearer access-token" } }));
  });
});
