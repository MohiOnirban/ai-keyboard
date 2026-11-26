// Simulator-only proxy for reproducible loading-state UI tests; run only with a local mock on 4000.
import http from "node:http";
import { setTimeout } from "node:timers/promises";
http.createServer(async (req, res) => {
  if (!["/v1/sessions", "/v1/session", "/v1/usage", "/v1/generate"].includes(req.url)) {
    res.writeHead(404).end();
    return;
  }
  if (req.url === "/v1/generate") await setTimeout(4000);
  const upstream = http.request({ hostname: "127.0.0.1", port: 4000, path: req.url,
    method: req.method, headers: req.headers }, (response) => {
    res.writeHead(response.statusCode ?? 502, response.headers);
    response.pipe(res);
  });
  upstream.on("error", () => res.writeHead(502).end());
  req.pipe(upstream);
}).listen(4133, "127.0.0.1");
