import { NextResponse } from "next/server";
import { getSessionAccessTokenSafely } from "./identity-client";
import { serverAuthorization } from "./session";

export async function sessionAuthorization(request: Request) {
  return serverAuthorization(await getSessionAccessTokenSafely(), request.headers.get("authorization"));
}

export function authRequired() {
  return NextResponse.json({ error: { code: "AUTH_REQUIRED", message: "A verified JobFlow session is required" }, meta: {} }, { status: 401 });
}

export async function forwardServiceResponse(response: Response) {
  const body = await response.text();
  return new NextResponse(body, { status: response.status, headers: { "content-type": response.headers.get("content-type") ?? "application/json" } });
}

export function serviceUnavailable(message: string) {
  return NextResponse.json({ error: { code: "SERVICE_UNAVAILABLE", message }, meta: {} }, { status: 503 });
}
