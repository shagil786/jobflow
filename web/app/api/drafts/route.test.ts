import { beforeEach, describe, expect, it, vi } from "vitest";

const getSessionAccessTokenSafely = vi.hoisted(() => vi.fn());
vi.mock("../../../lib/identity-client", () => ({ getSessionAccessTokenSafely }));

import { POST } from "./route";

describe("POST /api/drafts", () => {
  beforeEach(() => { vi.clearAllMocks(); getSessionAccessTokenSafely.mockResolvedValue("server-access-token"); });

  it("forwards grounded draft requirements to the draft service without changing them", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ draftId: "draft-1", recipient: "recruiter@example.com", subject: "Hello", body: "Draft", sent: false }), { status: 201 }));
    const payload = JSON.stringify({ applicationId: "app-1", contactId: "contact-1", resumeVersionId: "resume-1", draftType: "outreach", userInstructions: "Keep it concise" });
    const response = await POST(new Request("http://localhost:3000/api/drafts", { method: "POST", body: payload, headers: { authorization: "Bearer ignored" } }));
    expect(response.status).toBe(201);
    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8086/api/v1/drafts", expect.objectContaining({ method: "POST", body: payload, headers: expect.objectContaining({ authorization: "Bearer server-access-token" }) }));
  });
});
