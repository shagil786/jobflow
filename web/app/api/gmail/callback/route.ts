import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { createRemoteJWKSet, jwtVerify } from "jose";
import { getServerSessionMetadata } from "../../../../lib/identity-client";
import { readGmailOAuthConfig, validateGmailTokenResponse } from "../../../../lib/gmail-oauth";
import { readInternalServiceUrl } from "../../../../lib/service-url";

function failure(message: string, status=503) { const response=NextResponse.json({ error:{code:"GMAIL_CALLBACK_FAILED",message},meta:{} },{status}); for(const n of ["jobflow_gmail_state","jobflow_gmail_verifier","jobflow_gmail_nonce"]) response.cookies.delete(n); return response; }

export async function GET(request: Request) {
  const jar=await cookies(); const state=jar.get("jobflow_gmail_state")?.value; const verifier=jar.get("jobflow_gmail_verifier")?.value; const nonce=jar.get("jobflow_gmail_nonce")?.value; const query=new URL(request.url).searchParams;
  const providerError=query.get("error");
  if(providerError) { const description=query.get("error_description")?.trim(); return failure(description?`Google rejected the Gmail connection: ${description}`:`Google rejected the Gmail connection (${providerError})`,400); }
  if(!state||!verifier||!nonce||query.get("state")!==state||!query.get("code")) return failure("The Gmail sign-in response could not be verified",400);
  try {
    const identity=await getServerSessionMetadata(); if(!identity) return failure("A signed-in JobFlow session is required",401);
    const config=readGmailOAuthConfig({GMAIL_CLIENT_ID:process.env.GMAIL_CLIENT_ID,GMAIL_CLIENT_SECRET:process.env.GMAIL_CLIENT_SECRET,GMAIL_REDIRECT_URI:process.env.GMAIL_REDIRECT_URI});
    const body=new URLSearchParams({code:query.get("code")!,client_id:config.clientId,client_secret:config.clientSecret,redirect_uri:config.redirectUri,grant_type:"authorization_code",code_verifier:verifier});
    const tokenResponse=await fetch("https://oauth2.googleapis.com/token",{method:"POST",headers:{"content-type":"application/x-www-form-urlencoded"},body,cache:"no-store",signal:AbortSignal.timeout(8000)}); const raw=await tokenResponse.json() as {access_token?:unknown;refresh_token?:unknown;token_type?:unknown;expires_in?:unknown;id_token?:unknown}; if(!tokenResponse.ok) return failure("Google rejected the Gmail connection",502); const token=validateGmailTokenResponse(raw);
    const verified=await jwtVerify(token.idToken,createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs")),{issuer:["https://accounts.google.com","accounts.google.com"],audience:config.clientId}); if(verified.payload.nonce!==nonce) throw new Error("Gmail nonce mismatch");
    const profileResponse=await fetch("https://gmail.googleapis.com/gmail/v1/users/me/profile",{headers:{authorization:`Bearer ${token.accessToken}`},cache:"no-store",signal:AbortSignal.timeout(8000)}); if(!profileResponse.ok) { if(profileResponse.status===403) return failure("Google denied Gmail API access. Enable the Gmail API in the Google Cloud project that owns this OAuth client.",403); throw new Error("Gmail profile failed"); } const profile=await profileResponse.json() as {emailAddress?:string;historyId?:string}; if(!profile.emailAddress||!profile.historyId) throw new Error("Gmail profile is incomplete");
    const key=process.env.JOBFLOW_INTERNAL_SERVICE_KEY; const ingestion=readInternalServiceUrl("INGESTION_SERVICE_URL", "http://localhost:8082"); if(!key) throw new Error("internal key missing"); const saved=await fetch(`${ingestion}/internal/v1/gmail/connections`,{method:"POST",headers:{"content-type":"application/json","X-Internal-Service-Key":key},body:JSON.stringify({userId:identity.userId,tenantId:identity.tenantId,email:profile.emailAddress,refreshToken:token.refreshToken,historyId:profile.historyId}),cache:"no-store",signal:AbortSignal.timeout(8000)}); if(!saved.ok) throw new Error("ingestion connection failed");
    const response=NextResponse.redirect(new URL("/",request.url)); response.cookies.set("jobflow_gmail_connected","1",{httpOnly:true,secure:process.env.NODE_ENV==="production",sameSite:"lax",maxAge:60*60*24*30,path:"/"}); for(const n of ["jobflow_gmail_state","jobflow_gmail_verifier","jobflow_gmail_nonce"]) response.cookies.delete(n); return response;
  } catch { return failure("The Gmail connection could not be completed"); }
}
