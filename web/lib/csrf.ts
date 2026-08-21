export function isSameOrigin(requestOrigin: string | null, expectedOrigin: string): boolean {
  if (!requestOrigin) return false;
  try { return new URL(requestOrigin).origin === new URL(expectedOrigin).origin; } catch { return false; }
}
