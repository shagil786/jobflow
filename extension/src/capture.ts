import type { JobCaptureRequest } from "../../contracts/src/index";

const text = (selector: string): string | undefined => {
  const value = document.querySelector<HTMLElement>(selector)?.innerText.trim();
  return value || undefined;
};

/**
 * Extracts only visible job-page fields. It intentionally does not inspect
 * browsing history, private profiles, hidden DOM fields, or submit actions.
 */
export function captureVisibleJobPage(now = new Date()): JobCaptureRequest {
  const title = document.title.trim();
  const description = text("[data-job-description], article, main") ?? "";
  const host = window.location.hostname.replace(/^www\./, "");

  return {
    url: window.location.href,
    title,
    company: text("[data-company], [class*=company i], [class*=employer i]"),
    role: text("h1, [data-job-title], [class*=job-title i]") ?? "",
    location: text("[data-location], [class*=location i]"),
    source: host || "browser",
    descriptionPreview: description.slice(0, 2_000),
    capturedAt: now.toISOString(),
  };
}

export function isSafeCapture(payload: JobCaptureRequest): boolean {
  try {
    const parsed = new URL(payload.url);
    const hostname = parsed.hostname.toLowerCase();
    const privateHost = hostname === "localhost" || hostname === "::1" || hostname === "127.0.0.1" || hostname.startsWith("10.") || hostname.startsWith("192.168.") || hostname.startsWith("172.16.");
    return ["http:", "https:"].includes(parsed.protocol) && !privateHost && payload.title.trim().length > 0 && payload.role.trim().length > 0 && payload.source.trim().length > 0;
  } catch {
    return false;
  }
}
