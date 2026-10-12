// One-time Yoto login, run on a laptop: node scripts/yoto-login.mjs <client id>
// Opens Yoto's sign-in page, catches the redirect on localhost and prints the
// refresh token for YOTO_REFRESH_TOKEN. The Yoto app (yoto.dev → My apps) needs
// http://localhost:8765/callback as an allowed callback URL, and the scopes
// below turned on.
import { createHash, randomBytes } from "node:crypto";
import { createServer } from "node:http";
import { exec } from "node:child_process";

const clientId = process.argv[2] ?? process.env.YOTO_CLIENT_ID;
if (!clientId) {
  console.error("usage: node scripts/yoto-login.mjs <yoto client id>");
  process.exit(1);
}
const redirectUri = "http://localhost:8765/callback";
const verifier = randomBytes(32).toString("base64url");
const challenge = createHash("sha256").update(verifier).digest("base64url");
const state = randomBytes(12).toString("hex");

const authorize = new URL("https://login.yotoplay.com/authorize");
authorize.search = new URLSearchParams({
  response_type: "code",
  client_id: clientId,
  redirect_uri: redirectUri,
  scope: "openid profile offline_access family:library:view family:devices:view family:devices:control",
  audience: "https://api.yotoplay.com",
  code_challenge: challenge,
  code_challenge_method: "S256",
  state,
}).toString();

const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", redirectUri);
  if (url.pathname !== "/callback") return res.writeHead(404).end();
  if (url.searchParams.get("state") !== state) return res.writeHead(400).end("State mismatch; run the script again.");
  const code = url.searchParams.get("code");
  if (!code) return res.writeHead(400).end(`Yoto said: ${url.searchParams.get("error_description") ?? url.searchParams.get("error")}`);
  const tokenRes = await fetch("https://login.yotoplay.com/oauth/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "authorization_code", client_id: clientId, code, redirect_uri: redirectUri, code_verifier: verifier }),
  });
  const body = await tokenRes.json();
  if (!body.refresh_token) {
    res.writeHead(500).end("No refresh token came back; see the terminal.");
    console.error(body);
  } else {
    res.writeHead(200, { "Content-Type": "text/plain" }).end("Signed in. You can close this tab.");
    console.log(`\nYOTO_CLIENT_ID=${clientId}\nYOTO_REFRESH_TOKEN=${body.refresh_token}\n\nPut both in Vercel's environment variables, then redeploy.`);
  }
  server.close();
});

server.listen(8765, () => {
  console.log(`Opening ${authorize}`);
  const open = process.platform === "darwin" ? "open" : process.platform === "win32" ? "start" : "xdg-open";
  exec(`${open} "${authorize}"`);
});
