# Kotha Keys

Kotha Keys is the working name for a free Bangladesh-first keyboard. Both native apps offer English, raw Banglish, Bangla phonetic and a direct Bangla layout. Phonetic typing and local suggestions work offline. The on-demand AI panel offers Rewrite, Reply and Express. Users choose whether to apply the result and send the message themselves.

## Run a development build

From the repository root, start the shared mock service.

```sh
cd backend
npm ci
npm run dev
```

The server listens on loopback port 4000. Android emulators use the host alias 10.0.2.2. iOS simulators use localhost. The default mock provider and in-memory PostgreSQL ledger require no credentials. Installing either keyboard enables offline typing without the service.

- **Android** uses Kotlin InputMethodService and Jetpack Compose. Build the debug APK, install it, enable Kotha Keys in system settings, and select it as the current keyboard. [Android build and activation](apps/android/README.md)
- **iOS** uses SwiftUI and a UIKit keyboard extension. Run the KothaKeys scheme on an iPhone simulator. Add Kotha Keys in Settings → General → Keyboard → Keyboards. Switch keyboards with the globe. [iOS build, activation and signing](apps/ios/README.md)
- **AI** uses a Fastify TypeScript API, anonymous bearer sessions and a private PostgreSQL ledger. Live requests go through OpenRouter Responses with a configurable model. The intended model is openai/gpt-6-luna. [Backend configuration](backend/README.md)
- **Communication** includes versioned writing examples, a separate evaluation set and reproducible benchmark commands. [Communication evaluation](intelligence/README.md)
- **Typing** shares Avro grammar and a project-owned dictionary of common Banglish spellings. [Offline phonetic design and checks](docs/offline-typing.md)

## Hosted database

The ai-keyboard Supabase project has migration 20261125204106_private_usage. Its four tables are in keyboard_private, with RLS enabled and no mobile-client privileges. A dedicated keyboard_api database login connects over verified TLS. The developer Mac has a Git-ignored, mode-0600 backend/.env.hosted for this connection. The mobile apps only talk to Fastify.

To run the synthetic hosted-database check on this Mac, use the following command. It consumes one mock generation and revokes its temporary session.

```sh
cd backend
node --env-file=.env.hosted scripts/verify-supabase.mjs
```

To run the local API with hosted accounting, use `node --env-file=.env.hosted src/index.ts` in backend/. The hosted profile uses local port 4011 to coexist with the simulator service on 4000. Configure the desired port when connecting mobile builds. For another machine, provision the dedicated credentials in your secret manager and configure the certificate path. [Database setup](infrastructure/README.md)

## Verification

Run `./scripts/check.sh` after installing backend dependencies. It checks the backend build and tests, communication engine, evaluation harness, shared grammar and macOS-hosted Swift adapter. Platform-specific builds, emulator tests and XCTest workflows are documented in the mobile directories. [Delivery and verification](docs/delivery.md)

[Master implementation](docs/master.md) · [Engineering strategy](AGENTS.md) · [Shared architecture and API contract](shared/contract.md) · [Privacy](docs/privacy.md)
