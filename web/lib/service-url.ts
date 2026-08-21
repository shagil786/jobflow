export function readInternalServiceUrl(name: string, fallback: string): string {
  const value = process.env[name] ?? fallback;
  if (process.env.NODE_ENV === "production" && !value.startsWith("https://")) {
    throw new Error(`${name} must use HTTPS in production`);
  }
  return value.replace(/\/$/, "");
}
