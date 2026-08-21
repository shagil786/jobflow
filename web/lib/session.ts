import { cookies } from "next/headers";

export const SESSION_COOKIE = "jobflow_session_id";

export async function getSessionId(): Promise<string | null> {
  return (await cookies()).get(SESSION_COOKIE)?.value ?? null;
}

export function serverAuthorization(accessToken: string | null, _callerAuthorization?: string | null): string | null {
  return accessToken ? `Bearer ${accessToken}` : null;
}
