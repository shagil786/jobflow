import type { JobCaptureRequest } from "../../contracts/src/index";
import { isSafeCapture } from "./capture";

const CAPTURE_ENDPOINT = "https://app.jobflow.dev/api/applications/capture";

export interface CaptureSession {
  accessToken: string;
  tenantId: string;
  userId: string;
}

async function idempotencyKey(payload: JobCaptureRequest): Promise<string> {
  const url = new URL(payload.url);
  url.hash = "";
  const queryKeys = url.search.slice(1).split("&").filter(Boolean).map((pair) => decodeURIComponent(pair.split("=", 1)[0]));
  queryKeys.filter((key) => key.startsWith("utm_") || key === "ref").forEach((key) => url.searchParams.delete(key));
  const bytes = new TextEncoder().encode(`${url.toString()}|${payload.role.trim().toLowerCase()}`);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return `capture:${Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("")}`;
}

export async function submitCapture(payload: JobCaptureRequest, session: CaptureSession): Promise<{ accepted: true; applicationId: string; idempotencyKey: string }> {
  if (!isSafeCapture(payload)) throw new Error("Capture is missing a valid public job URL or title");
  const key = await idempotencyKey(payload);
  const response = await fetch(CAPTURE_ENDPOINT, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${session.accessToken}`, "x-tenant-id": session.tenantId, "x-user-id": session.userId, "x-idempotency-key": key },
    body: JSON.stringify(payload),
  });
  const result = await response.json().catch(() => undefined) as { id?: string } | undefined;
  if (!response.ok) throw new Error(`Capture failed with status ${response.status}`);
  if (!result?.id) throw new Error("Capture service returned no persisted application ID");
  return { accepted: true, applicationId: result.id, idempotencyKey: key };
}
