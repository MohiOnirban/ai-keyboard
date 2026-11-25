# Keyboard API

From this directory, run:

```sh
npm ci
npm run build
npm test
npm run dev
```

Node 22.18 or newer is required. The default address is `127.0.0.1:4000`. The server runs the local mock with an in-memory PostgreSQL ledger. In-memory sessions reset when the server stops. To retain mock sessions across restarts, set `MOCK_DATABASE_DIR` to a dedicated writable directory.

```sh
curl -fsS http://127.0.0.1:4000/health
curl -fsS http://127.0.0.1:4000/v1/sessions -H 'Content-Type: application/json' -d '{}'
```

The paths and request formats are in the [shared contract](../shared/contract.md). Port 4000 is reachable as `10.0.2.2:4000` from a standard Android emulator and `localhost:4000` from an iOS simulator. For a physical device on a trusted local network, `HOST=0.0.0.0` enables access to the Mac's private LAN address. Use synthetic text on local HTTP. The compiled server runs with `npm start` after `npm run build`.

The usage allowance defaults to 20 successful generations. Usage resets at 00:00 UTC. Generation attempts and provider spend count toward the UTC day and month in which the server admits the request. Failures release the success slot. One idempotency UUID represents one user action. Every retry with that UUID receives 409 after the request is reserved, so the server never has to retain a draft or suggestion to replay it. A new action uses a new UUID.

The API accepts optional style and relationship strings of up to 80 UTF-16 units. Preference fields accept up to 80 each, except custom instructions, which may have up to 500. The text limit is 1,600 UTF-16 units. A separate input-token bound covers the combined text, preferences, instructions and structured-output framing. The service uses a conservative UTF-8 byte estimate and records the actual token counts supplied by the provider.

## Live configuration

Copy [the environment example](.env.example) to `.env` to override settings. Keep service secrets in the server environment. Apply the database migration and provision the private database role first. [Database setup](../infrastructure/README.md)

Live mode requires `AI_PROVIDER=openrouter`, a TLS-verified PostgreSQL connection, a server-side OpenRouter key, positive input and output price ceilings in dollars per million tokens, and positive daily and monthly budgets. The model defaults to `openai/gpt-6-luna`. Set prices and caps after verifying the approved model, eligible provider routes and budget. Use the same settings across all service replicas and a dedicated provider key with its own spending limit. `AI_ENABLED=false` disables admission on restart. [GPT-6 Luna model and prices](https://openrouter.ai/openai/gpt-6-luna), [OpenRouter key limits](https://openrouter.ai/docs/api/reference/limits)

The service sends a single non-streaming Responses request with reasoning effort `none`, `store:false`, a strict JSON suggestion schema and an output-token cap. It requests ZDR routes, denies providers that collect data for training and caps each provider's accepted token rates. Verify routing, account prompt logging and retention settings before directing private text to live inference. The API sends the explicitly provided text and preferences to OpenRouter and its routed model provider when live. Provider policies and legal or abuse exceptions can apply. [Responses API](https://openrouter.ai/docs/api/api-reference/responses/create-a-response), [OpenRouter privacy](https://openrouter.ai/docs/guides/privacy)

## Usage and privacy

The database records only opaque session identifiers, bearer digests, expiry and revocation times, short-lived rate counters, idempotency UUIDs, generation status, budget reservations, reported token usage, reported or estimated cost and latency. Fastify request logging is disabled, and errors expose fixed messages. Keep request bodies, authorization headers and provider content out of proxy and deployment telemetry.

Anonymous installations can reset credentials, reinstall or change network addresses. Session issuance limits, installation and address rate limits, concurrency and global spending caps limit the effect without device fingerprinting. Address rate keys are HMACs scoped to a single minute. Session credentials expire after 90 days and can be revoked at any time.

A dropped client connection invalidates the pending result. A provider call already dispatched is allowed to reach its deadline to capture billing information. When the server cannot establish the bill, it retains the entire reservation against the period's caps. Abandoned in-flight requests release concurrency and success slots after their lease while retaining spending reservations. An in-flight provider call can accrue costs after shutdown. Delays, missing billing data and upstream changes can affect reconciliation. Unexpected usage above a reserved bound is recorded and the output is withheld.

Automated tests cover session lifecycle, strict UTF-16 validation, rate and quota enforcement, durable reservation and settlement, idempotency under concurrency, restrictive database role grants, UTC reset boundaries, refusal handling and the OpenRouter request and response shapes using fixtures.

## Benchmark the API

From backend/, run the checked-in fictional scenarios against an isolated local instance of the API. The output path must be new. The evaluator reports the observed end-to-end latency for the in-process Fastify request, including database work. This excludes the mobile network round trip.

```sh
mkdir -p ../artifacts
AI_PROVIDER=mock AI_DAILY_LIMIT=114 AI_INSTALL_REQUESTS_PER_MINUTE=114 node scripts/benchmark.mjs ../artifacts/mock-run.json
node ../intelligence/evaluate.ts ../artifacts/mock-run.json
```

For an authorized live benchmark, use a dedicated environment configuration for the run with the live provider, the normal private PostgreSQL ledger, approved price ceilings, global caps, a sufficient benchmark session allowance and rate limits. Configure BENCHMARK_MAX_USD to the approved total for the run. The script requires the explicit --live option and checks the worst-case sum of reservations. If a request fails, it writes the completed synthetic observations and stops. Costs use the same reported-or-estimated accounting as the backend. Credentials and private user drafts are excluded from the benchmark file.

## Deploy the service

Use a private build workspace with Node 22.18+ and the repository layout intact. From backend/, run `npm ci`, `npm run build` and `npm test`. Deploy the resulting dist directory together with package.json and package-lock.json. Run `npm ci --omit=dev` beside those files on the runtime host.

Set NODE_ENV=production, DATABASE_URL, DATABASE_TLS=true, DATABASE_CA_FILE when needed, RATE_LIMIT_SECRET, HOST and PORT in the server environment. Start with AI_PROVIDER=mock for the installation check. Put TLS termination in front of the API, restrict direct access to its internal listener, and set TRUST_PROXY_CIDRS only for the actual ingress addresses. Run `npm start` under the host's process supervisor. A GET to /health should return HTTP 200 and an ok status from the compiled service and its database.

Use the HTTPS origin when building the device clients. Once the account, model, eligible route, retention settings and spending caps are configured, the same mobile builds can use the live provider by changing the server environment and restarting the service. Changing AI_ENABLED to false and restarting disables generation while offline typing remains available. To roll back a service version, redeploy the previous compatible server artifact using the same ledger.
