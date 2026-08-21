import { afterEach, describe, expect, it, vi } from "vitest";

const { getServerSessionMetadata } = vi.hoisted(() => ({ getServerSessionMetadata: vi.fn() }));

vi.mock("../../../../lib/identity-client", () => ({ getServerSessionMetadata }));

import { GET } from "./route";

describe("GET /api/gmail/status", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllEnvs();
    getServerSessionMetadata.mockReset();
  });

  it("requires a verified server session", async () => {
    getServerSessionMetadata.mockResolvedValue(null);

    const response = await GET();

    expect(response.status).toBe(401);
  });

  it("returns a controlled outage response when ingestion is unavailable", async () => {
    getServerSessionMetadata.mockResolvedValue({ userId: "user-1", tenantId: "tenant-1" });
    vi.stubEnv("JOBFLOW_INTERNAL_SERVICE_KEY", "internal-test-key");
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("", { status: 503 }));

    const response = await GET();

    expect(response.status).toBe(503);
    expect(await response.json()).toMatchObject({ error: { code: "INGESTION_UNAVAILABLE" } });
  });
});
