// Simulator-only proxy for reproducible loading, cancellation and failure states.
import http from "node:http";
import { setTimeout } from "node:timers/promises";

http.createServer(async (req, res) => {
  if (!["/v1/sessions", "/v1/session", "/v1/usage", "/v1/generate"].includes(req.url)) {
    res.writeHead(404).end();
    return;
  }
  let body;
  if (req.url === "/v1/generate") {
    try {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      body = Buffer.concat(chunks);
    } catch {
      return;
    }
    await setTimeout(4000);
    if (res.destroyed) return;
    let request;
    try { request = JSON.parse(body); } catch { /* Forward malformed requests for normal validation. */ }
    if (request?.text === "mock failure seed." && request?.style === "professional") {
      res.writeHead(503, {
        "content-type": "application/json",
        "kotha-error-code": "provider_response",
      }).end(JSON.stringify({ error: "AI is temporarily unavailable." }));
      return;
    }
  }
  const upstream = http.request({ hostname: "127.0.0.1", port: 4000, path: req.url,
    method: req.method, headers: req.headers }, (response) => {
    res.writeHead(response.statusCode ?? 502, response.headers);
    response.pipe(res);
  });
  upstream.on("error", () => res.writeHead(502).end());
  if (body) upstream.end(body);
  else req.pipe(upstream);
}).listen(Number(process.env.KOTHA_TEST_PROXY_PORT ?? 4133), "127.0.0.1");
