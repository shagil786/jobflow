import { describe, expect, it } from "vitest";
import { isSameOrigin } from "./csrf";

describe("CSRF origin validation", () => {
  it("accepts same-origin mutations and rejects cross-origin mutations", () => {
    expect(isSameOrigin("https://app.jobflow.dev", "https://app.jobflow.dev")).toBe(true);
    expect(isSameOrigin("https://app.jobflow.dev", "https://evil.example")).toBe(false);
  });
});
