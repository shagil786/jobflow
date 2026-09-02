const RECOMMENDATION_DIGEST = /(?:now,? take these next steps|view similar jobs you may be interested in)/i;

/** Formats on-demand Gmail text for a readable preview without changing stored evidence. */
export function formatGmailMessageBody(value?: string): string {
  if (!value) return "Message body unavailable";

  let body = value.replace(/\r\n?/g, "\n").replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, "");
  const digestStart = body.search(RECOMMENDATION_DIGEST);
  if (digestStart >= 0) {
    body = body.slice(0, digestStart).trim();
  }

  body = body
    .split("\n")
    .map(line => line.trim().replace(/^-{8,}$/, ""))
    .join("\n")
    .replace(/https?:\/\/\S{180,}/g, "[long tracking link omitted]")
    .replace(/\n{3,}/g, "\n\n")
    .trim();

  return body.length > 12000 ? `${body.slice(0, 12000).trim()}\n\n[Message preview truncated]` : body;
}
