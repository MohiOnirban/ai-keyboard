# Kotha Keys development delivery

The Kotlin input method and Swift keyboard extension both type English, raw Banglish, phonetic Bangla and direct Bangla offline. The companion apps provide setup, preferences, practice and usage controls. Rewrite, Reply and Express use a shared Fastify API and private PostgreSQL accounting. A suggestion stays in preview until the user accepts it. The normal development builds point to the local mock service on port 4000.

## Install

- **Android emulator** — Install `artifacts/KothaKeys-android-debug.apk` with `adb install -r artifacts/KothaKeys-android-debug.apk`. Open Kotha Keys, tap **Enable keyboard**, enable it in system settings, return and tap **Choose keyboard**. Tap **文** for language switching. The Android guide includes physical-device installation and release signing. [Android guide](../apps/android/README.md)
- **iOS simulator** — On an Apple Silicon Mac with a booted compatible iPhone simulator, extract `artifacts/KothaKeys-iOS-simulator.zip` and run `xcrun simctl install booted KothaKeys.app` from the extraction directory. Launch the app with `xcrun simctl launch booted dev.kothakeys.app`. In Settings → General → Keyboard → Keyboards, add Kotha Keys, then use the globe in a text field to select it. Full Access is needed for the extension's AI actions. [iOS guide](../apps/ios/README.md)
- **Try phonetic typing** — Select Phonetic and enter `Ami tomake bhalobashi`, committing each word with Space. The expected result is **আমি তোমাকে ভালোবাসি**. The next Space starts the next word.
- **Try mock AI** — In `backend/`, run `npm ci` and `npm run dev`. The default local server offers 20 successful suggestions per installation per UTC day. Live mode uses server-side credentials, model settings and explicit daily and monthly limits. [Backend setup](../backend/README.md)

## Verification

| Area | Observed result |
| --- | --- |
| Backend | Node 24.14.1 compiled the TypeScript service. Twelve API, provider and ledger tests passed, including an interrupted HTTP connection during generation followed by a successful new action with the daily allowance intact. |
| Hosted database | Synthetic Fastify integration on the supplied Supabase project verified session issuance, generation accounting, duplicate rejection, revocation and denial of a revoked credential. The four tables use RLS in a private schema. The service connects over verified TLS with a dedicated role. [Database setup](../infrastructure/README.md) |
| Communication | Nine corpus, isolation and language tests passed. The 500 authored variations cover 100 source messages. The mock API generated all 114 evaluation suggestions at zero provider cost. [Communication evaluation](../intelligence/README.md) |
| Offline phonetics | Both adapters gave the expected first choice on 25 targeted cases. The Swift adapter passed 26 fixtures and all 150 curated dictionary entries. Kotlin matched the pinned upstream parser on 8,472 contextual inputs. [Phonetic checks](offline-typing.md) · [Riti decision](riti-evaluation.md) |
| Android | The debug APK and unsigned release APK built. The signed debug APK passed signature verification. Offline typing in an unsent Google Messages draft on Android 15 produced **আমি তোমাকে ভালোবাসি** with Wi-Fi and cellular data disabled. [Android verification](../apps/android/VERIFICATION.md) |
| iOS | Xcode built the simulator app with its embedded keyboard and an unsigned arm64 device release. Three simulator workflows verified onboarding, session reset, explicit preview and insertion, and phonetic typing with Full Access disabled. A separate live GPT-6 Luna keyboard test passed. The default simulator build was reinstalled and the Full Access off workflow passed again. [iOS verification](../apps/ios/README.md) |

The privacy review led to changes in clipboard handling, explicit Reply input, HTTP redirect handling, stale editor targets, duplicate taps, session revocation and recognized sensitive fields. Both clients restrict their anonymous bearer to the configured backend origin. The server stores usage and cost metadata. [Privacy details](privacy.md)

## Release boundaries

The repository's origin points to the supplied GitHub repository. The API ran on loopback for testing, and the supplied Supabase project holds the usage schema. Distribution to physical iPhones requires Apple team signing and provisioning. Physical-device keyboard performance, broader messaging-app compatibility and native Bangladeshi speaker acceptance remain release checks. Voice input, swipe typing and extensive dictionary prediction are deferred under the master implementation. [Device and test notes for Android](../apps/android/VERIFICATION.md) · [iOS signing and activation](../apps/ios/README.md)
