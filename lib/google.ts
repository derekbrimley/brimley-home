// Google service-account auth without the googleapis package: sign a JWT with
// the account's private key and trade it for an access token. Tokens are cached
// per scope set for the life of the function instance (stateless otherwise).
import { createSign } from "crypto";

interface ServiceAccount {
  client_email: string;
  private_key: string;
}

const cache = new Map<string, { token: string; expiresAt: number }>();

function b64url(input: Buffer | string): string {
  return Buffer.from(input).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function loadServiceAccount(json = process.env.GOOGLE_SERVICE_ACCOUNT_JSON): ServiceAccount | null {
  if (!json) return null;
  try {
    const parsed = JSON.parse(json) as Partial<ServiceAccount>;
    if (!parsed.client_email || !parsed.private_key) return null;
    return { client_email: parsed.client_email, private_key: parsed.private_key };
  } catch {
    return null;
  }
}

export function buildAssertion(sa: ServiceAccount, scopes: string[], nowSec = Math.floor(Date.now() / 1000)): string {
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(
    JSON.stringify({
      iss: sa.client_email,
      scope: scopes.join(" "),
      aud: "https://oauth2.googleapis.com/token",
      iat: nowSec,
      exp: nowSec + 3600,
    })
  );
  const signer = createSign("RSA-SHA256");
  signer.update(`${header}.${claims}`);
  const signature = b64url(signer.sign(sa.private_key));
  return `${header}.${claims}.${signature}`;
}

export async function getAccessToken(scopes: string[]): Promise<string> {
  const key = scopes.join(" ");
  const hit = cache.get(key);
  if (hit && hit.expiresAt > Date.now() + 60_000) return hit.token;

  const sa = loadServiceAccount();
  if (!sa) throw new Error("GOOGLE_SERVICE_ACCOUNT_JSON is not set");

  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion: buildAssertion(sa, scopes),
  });
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  if (!res.ok) throw new Error(`Google token exchange failed: ${res.status} ${await res.text()}`);
  const data = (await res.json()) as { access_token: string; expires_in: number };
  cache.set(key, { token: data.access_token, expiresAt: Date.now() + data.expires_in * 1000 });
  return data.access_token;
}

export async function googleGet<T>(url: string, scopes: string[]): Promise<T> {
  const token = await getAccessToken(scopes);
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) throw new Error(`Google API ${res.status}: ${await res.text()}`);
  return (await res.json()) as T;
}

export async function googlePut<T>(url: string, scopes: string[], body: unknown): Promise<T> {
  const token = await getAccessToken(scopes);
  const res = await fetch(url, {
    method: "PUT",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`Google API ${res.status}: ${await res.text()}`);
  return (await res.json()) as T;
}

export const SHEETS_RW = ["https://www.googleapis.com/auth/spreadsheets"];
export const CALENDAR_RO = ["https://www.googleapis.com/auth/calendar.readonly"];
