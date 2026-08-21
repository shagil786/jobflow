import { afterEach, describe, expect, it, vi } from "vitest";
import { readInternalServiceUrl } from "./service-url";

describe("internal service URLs", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("allows localhost HTTP for local development", () => {
    vi.stubEnv("NODE_ENV", "development");
    expect(readInternalServiceUrl("JOB_SERVICE_URL", "http://localhost:8091/")).toBe("http://localhost:8091");
  });

  it("rejects HTTP internal services in production", () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("JOB_SERVICE_URL", "http://job-service.internal:8091");
    expect(() => readInternalServiceUrl("JOB_SERVICE_URL", "http://localhost:8091")).toThrow("JOB_SERVICE_URL must use HTTPS in production");
  });

  it("accepts HTTPS internal services in production", () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("INGESTION_SERVICE_URL", "https://ingestion.example.test/");
    expect(readInternalServiceUrl("INGESTION_SERVICE_URL", "http://localhost:8082")).toBe("https://ingestion.example.test");
  });
});
