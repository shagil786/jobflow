import { getSessionId } from "./session";

const identityServiceUrl = process.env.IDENTITY_SERVICE_URL ?? "http://localhost:8081";

function internalHeaders(): HeadersInit {
  if (process.env.NODE_ENV === "production" && !identityServiceUrl.startsWith("https://")) {
    throw new Error("IDENTITY_SERVICE_URL must use HTTPS in production");
  }
  const key = process.env.JOBFLOW_INTERNAL_SERVICE_KEY;
  if (!key) throw new Error("JOBFLOW_INTERNAL_SERVICE_KEY is not configured");
  return { "X-Internal-Service-Key": key };
}

export async function createServerSession(input: {
  userId: string; tenantId: string; provider: string; accessToken: string;
  refreshToken?: string; accessTokenExpiresAt: string;
}): Promise<{ sessionId: string; expiresAt: string }> {
  const response = await fetch(`${identityServiceUrl}/internal/v1/sessions`, {
    method: "POST", headers: { ...internalHeaders(), "content-type": "application/json" },
    body: JSON.stringify(input), cache: "no-store", signal: AbortSignal.timeout(5000),
  });
  if (!response.ok) throw new Error("identity session creation failed");
  return await response.json() as { sessionId: string; expiresAt: string };
}

export async function getSessionAccessToken(): Promise<string | null> {
  const sessionId = await getSessionId();
  if (!sessionId) return null;
  const response = await fetch(`${identityServiceUrl}/internal/v1/sessions/${encodeURIComponent(sessionId)}/access-token`, {
    method: "POST", headers: internalHeaders(), cache: "no-store", signal: AbortSignal.timeout(5000),
  });
  if (!response.ok) { console.info(`[jobflow-auth] identity-access-status=${response.status}`); return null; }
  const body = await response.json() as { accessToken?: unknown };
  return typeof body.accessToken === "string" ? body.accessToken : null;
}

export async function revokeServerSession(sessionId: string | null): Promise<void> {
  if (!sessionId) return;
  const response = await fetch(`${identityServiceUrl}/internal/v1/sessions/${encodeURIComponent(sessionId)}/revoke`, {
    method: "POST", headers: internalHeaders(), cache: "no-store", signal: AbortSignal.timeout(5000),
  });
  if (!response.ok) throw new Error("identity session revocation failed");
}

export async function getServerSessionMetadata(): Promise<{ userId: string; tenantId: string } | null> {
  const sessionId = await getSessionId();
  if (!sessionId) return null;
  const response = await fetch(`${identityServiceUrl}/internal/v1/sessions/${encodeURIComponent(sessionId)}/metadata`, { method: "POST", headers: internalHeaders(), cache: "no-store", signal: AbortSignal.timeout(5000) });
  if (!response.ok) return null;
  return await response.json() as { userId: string; tenantId: string };
}

export async function getSessionAccessTokenSafely(): Promise<string | null> {
  try { return await getSessionAccessToken(); } catch { return null; }
}
