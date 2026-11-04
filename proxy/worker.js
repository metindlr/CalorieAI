// Cloudflare Worker: Gemini anahtarını APK'dan uzak tutar.
// Deploy:  npm i -g wrangler && wrangler deploy
//   wrangler secret put GEMINI_API_KEY
//   wrangler secret put APP_TOKEN        (uygulamadaki PROXY_TOKEN ile aynı)
// Ardından Cloudflare panelinde bu Worker'a "Rate limiting" kuralı ekle (ör. IP başına dakikada 10 istek).
export default {
  async fetch(req, env) {
    if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
    if (req.headers.get("X-App-Token") !== env.APP_TOKEN) return new Response("Unauthorized", { status: 401 });
    const body = await req.text();
    if (body.length > 3_000_000) return new Response("Too large", { status: 413 });
    const r = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
      { method: "POST", headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY }, body }
    );
    return new Response(r.body, { status: r.status, headers: { "Content-Type": "application/json" } });
  },
};
