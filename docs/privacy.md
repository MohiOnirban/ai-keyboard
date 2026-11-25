# Keyboard privacy

Typing, phonetic conversion, suggestions and appearance preferences work locally. The keyboard does not monitor conversations, collect ordinary keystrokes or poll the clipboard. A user tap reads the clipboard. A separate AI generation action sends the supplied draft and selected communication preferences to the configured Fastify server. Reply uses explicitly supplied incoming text. The user reviews a suggestion and chooses whether to insert it.

The companion explains keyboard activation and AI networking. iOS may substitute the system keyboard in protected fields, and some host applications disable third-party keyboards. AI controls are suppressed for recognizable sensitive fields. Operating systems and host apps control the context available to each keyboard.

The backend records usage counts, opaque session identifiers, billing and timing information. The usage database has no fields for drafts, clipboard text or generated suggestions. Session reset revokes a credential. Clearing preferences removes local settings. Volatile mock sessions last for the server process unless database persistence is configured.

In live mode OpenRouter routes explicit requests to the configured inference provider. The service requests restricted data collection and zero-retention eligible routes, with storage disabled. Account settings, provider terms and exceptions affect retention. Operational proxies must also exclude authorization headers, bodies, model responses and clipboard data from logs. [OpenRouter privacy](https://openrouter.ai/docs/guides/privacy)

Local mock mode sends text to the development server, where it is handled by the local deterministic generator. Mock mode makes no inference-provider calls. The free daily allowance is installation-scoped. Resetting installations and changing networks can bypass some installation-level controls.
