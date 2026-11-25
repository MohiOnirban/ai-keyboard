# MASTER IMPLEMENTATION — Bangladesh-First Personal AI Keyboard

## 1. Assignment

Act as the principal software architect, senior Android engineer, senior iOS engineer, AI engineer, product designer, security engineer and quality assurance engineer.

Design, implement, build and test a complete, functional AI-powered keyboard for Android and iOS.

You are authorized to make reasonable technical decisions independently. Do not stop after creating a roadmap, documentation, mockup or incomplete scaffold. Proceed with implementation, resolve errors and run tests wherever the environment permits.

Inspect the existing development environment before installing anything. Reuse installed SDKs, runtimes, databases and tools wherever appropriate.

## 2. Product vision

Build a beautiful, lightweight, personalized AI texting assistant specifically designed for Bangladeshi people.

This is not primarily a grammar checker, translator or productivity tool.

Its purpose is helping people express their thoughts, feelings, humor, affection and personality naturally and creatively.

The experience should feel like having a culturally intelligent friend who is exceptionally good at communication, directly inside the keyboard.

The entire initial product is free.

Do not implement payment gateways, subscriptions, paid credits, checkout screens, advertising or premium restrictions.

## 3. Architecture

Use a single monorepo containing:

- Native Android application and keyboard service using Kotlin.
- Jetpack Compose for Android companion application screens.
- Native iOS companion application using SwiftUI.
- Native iOS keyboard extension using Swift and UIKit.
- One shared Node.js and TypeScript backend using Fastify.
- PostgreSQL for minimal durable server-side usage accounting.
- OpenAI Responses API for AI generation.
- Shared API contracts, design specifications, communication styles and AI evaluation datasets.

Use the simplest architecture that satisfies the requirements.

Do not introduce Flutter, microservices, Redis, Kubernetes or unnecessary external dependencies.

Keep the backend provider-independent enough to change AI models without rebuilding mobile applications.

## 4. Core product functionality

Implement three primary AI modes.

**Rewrite**

Transform a message the user has already written into a more creative, natural or emotionally appropriate version.

**Reply**

Generate a response to an incoming message explicitly copied, pasted or otherwise provided by the user.

**Express**

Convert a rough thought, intention or feeling into a complete message.

Example:

Input: "তোমাকে ভালো লাগে।"

Flirty: "তুমি কি কোনো জাদু জানো? দিন দিন তোমার মায়ায় বেশি পড়ে যাচ্ছি।"

Romantic: "তোমার সঙ্গে কথা বললেই মনে হয় দিনের সবচেয়ে সুন্দর সময়টা তখনই শুরু হয়।"

Funny: "তোমাকে ভালো লাগে, কিন্তু আমার ইগোর কাছে বিষয়টা গোপন রেখো।"

Poetic: "তুমি যেন আমার ব্যস্ত শহরে হঠাৎ নেমে আসা এক বিকেলের বৃষ্টি।"

Bold: "তোমাকে ভালো লাগে। আর সেটা লুকানোর ইচ্ছাও দিন দিন কমে যাচ্ছে।"

These examples establish the desired creativity and naturalness. Do not repeatedly reuse them as generated responses.

## 5. Communication intelligence

Support the following communication styles:

- Flirty
- Romantic
- Funny
- Poetic
- Witty
- Sarcastic
- Confident
- Bold
- Subtle
- Mature
- Emotional
- Respectful
- Professional
- Savage
- Comforting
- Apologetic
- Culturally inspired
- Custom instructions

Allow users to select their favorite styles and customize which styles appear in the keyboard toolbar.

Support three intensity levels: subtle, balanced and bold.

Optional relationship context should include friends, crushes, romantic partners, family members, teachers, colleagues and strangers.

Keep these controls optional. A user should be able to generate a good rewrite with minimal interaction.

The AI must preserve the user's intended meaning, avoid inventing personal experiences, respect interpersonal boundaries and produce messages appropriate to the provided context.

## 6. Bangladeshi cultural intelligence

Prioritize:

- Natural Bangladeshi Bengali.
- Natural Banglish.
- English commonly used by Bangladeshis.
- Code-switching between Bengali and English.
- Appropriate use of আপনি, তুমি and তুই.
- Bangladeshi humor, slang and social etiquette.
- Bengali literature, cinema, songs, memes and cultural references.
- Natural romantic and emotional expressions.
- Everyday conversations involving friends, family, relationships, education and work.

Avoid robotic Bengali, unnecessarily formal vocabulary, excessive emojis, forced cultural references and stereotypical assumptions.

Generate culturally appropriate references only when relevant and accurate. Do not invent quotations or misattribute lines to real people.

Create a version-controlled communication dataset with an initial target of 500 varied, carefully constructed examples.

Create a separate evaluation dataset containing at least 100 realistic communication scenarios. Keep evaluation examples separate from prompt examples.

Do not train a foundation model or introduce fine-tuning initially. Start with concise system instructions, relevant examples and structured personalization.

## 7. AI integration

Use OpenAI's Responses API.

Initial model configuration:

- Model: gpt-6-luna
- Reasoning effort: none
- Store: false
- Bounded output tokens appropriate for short messages
- Model and generation limits configurable through server environment variables

Support a mock AI provider for development and testing without API costs.

Never embed OpenAI API keys in mobile applications, source control, compiled packages or client-side configuration.

All production AI requests must pass through the backend.

Keep each request independent unless the user explicitly supplies additional context.

Treat submitted messages as untrusted text, not system instructions. Do not permit instructions embedded in messages to override application behavior.

Return structured, validated results. Handle model refusals and invalid outputs gracefully.

Measure input tokens, output tokens, latency, generation success and estimated API cost without storing private message content.

## 8. Normal keyboard functionality

The keyboard must be genuinely useful without AI.

Implement:

- English QWERTY layout.
- Native Bangla character layout.
- Offline phonetic Banglish-to-Bangla typing.
- Raw Banglish typing.
- Language switching.
- Uppercase and lowercase.
- Shift and caps lock.
- Numbers, punctuation and symbols.
- Emoji input.
- Space, enter and backspace.
- Long-press behavior and continuous deletion.
- Basic cursor movement and text editing.
- Clipboard actions initiated explicitly by the user.
- Haptic feedback settings.
- Light and dark themes.
- Responsive portrait and landscape layouts.
- Appropriate layouts for numeric, email and URL fields.
- Basic local word suggestions and autocorrection where reliable.

Correctly handle Bengali vowel signs, hasanta, conjunct consonants, composing text, Unicode grapheme clusters and deletion.

Investigate appropriately licensed reusable keyboard engines and phonetic transliteration dictionaries before implementing them from scratch. Document licenses and attribution obligations.

Do not sacrifice normal typing responsiveness for AI functionality.

Advanced voice typing, swipe typing and extensive dictionary prediction may be deferred rather than implemented unreliably.

## 9. AI keyboard interaction

The primary workflow:

1. User opens a compatible messaging application.
2. User types a message normally.
3. User taps the AI button.
4. Keyboard captures the selected text or reliably accessible draft.
5. User chooses a communication style.
6. AI generates a suggestion.
7. User previews the result.
8. User may regenerate, edit, cancel or accept.
9. The accepted suggestion replaces the intended text or is inserted safely.
10. User sends the message manually.

Never automatically send messages.

Never silently inspect or retrieve entire conversations.

If the platform cannot reliably identify the entire draft, support rewriting selected text, explicitly provided text or a bounded portion of text.

Before applying any generated result, verify that the input field and original target text have not changed. If they have changed, do not overwrite the new content. Offer safe insertion or copying instead.

Cancel or invalidate outstanding generation requests when the keyboard closes or the input target changes.

Maintain normal keyboard usability while AI generation is running.

## 10. Android implementation

Implement a proper native InputMethodService.

Use Android InputConnection APIs for text composition, selection, insertion and replacement.

Handle keyboard lifecycle events, changes in focused input fields and editor actions correctly.

Disable AI processing in password fields and other recognizable sensitive input modes.

Do not request Accessibility Service permissions merely to read messaging applications.

Optionally implement ACTION_PROCESS_TEXT as a secondary integration if it can be delivered reliably without distracting from the core keyboard.

Provide a companion application that guides users through enabling and selecting the keyboard.

Generate an installable debug APK and document the release build procedure.

## 11. iOS implementation

Implement a containing iOS application and a native keyboard extension using UIInputViewController and UITextDocumentProxy.

Support system keyboard switching, changing keyboard dimensions and input traits.

Use Full Access only for functionality that requires networking or permitted shared-container operations.

Clearly explain Full Access during onboarding.

Normal typing must remain functional when Full Access is disabled or the network is unavailable.

Use an App Group or other appropriate platform-supported mechanism for sharing minimal preferences and session information between the containing application and keyboard extension.

Account for limited document context, extension memory constraints and applications that disallow third-party keyboards.

Do not claim access to an entire message or conversation unless the platform actually exposes it.

Follow current Apple keyboard extension and App Store requirements.

Generate a simulator-compatible build and document physical-device installation, signing and TestFlight requirements.

## 12. Companion application

Build a clean, modern application containing:

- Welcome and onboarding.
- Keyboard activation guide.
- Interactive practice area.
- AI communication playground.
- Preferred language settings.
- Favorite communication styles.
- Personal communication preferences.
- Keyboard appearance.
- Free daily AI usage display.
- Privacy and data controls.
- Help and troubleshooting.

Users should be able to practice AI rewriting before enabling the system keyboard.

Support local personalization, including preferred message length, humor, formality, emoji frequency, language and custom communication instructions.

Do not automatically learn from private conversations.

No payment, pricing, subscription, checkout or advertising screens.

## 13. Free usage system

The product is free during the initial release.

Initial proposed allowance: 20 successful AI generations per installation per day.

The allowance must be configurable from the backend without requiring a mobile application update.

Implement anonymous installation sessions without mandatory registration.

Use secure, revocable session credentials and appropriate secure platform storage.

Implement:

- Daily server-side usage accounting.
- Request rate limits.
- Request length limits.
- AI token limits.
- Concurrent request limits.
- Idempotent generation requests.
- Global daily and monthly API spending limits.
- Configurable AI shutdown switch.

Reserve budget atomically before submitting billable requests. Reconcile reserved usage against actual provider usage afterward.

Failed requests should not deduct successful-generation allowances, but any incurred provider costs must still be accounted for.

Prevent ordinary duplicate requests from consuming multiple allowances.

Recognize that anonymous installations cannot provide perfect abuse prevention. Document remaining limitations without introducing invasive device fingerprinting.

When limits are reached, normal keyboard functionality must continue working.

## 14. Privacy and security

A system keyboard handles highly sensitive information. Treat privacy as a fundamental product requirement.

- Never record ordinary keystrokes.
- Never silently upload messages.
- Never collect complete inboxes.
- Never upload background clipboard contents.
- Never store private generated messages or submitted drafts in the backend database.
- Never include message contents in application logs, error reporting or analytics.
- Never process passwords or recognized sensitive fields.
- Transmit text to the backend only following an explicit AI action.
- Explain that submitted text is processed by an external AI provider and disclose applicable retention conditions.
- Use HTTPS for production communication.
- Validate every server request.
- Store secrets securely.
- Provide controls for clearing local preferences and resetting anonymous session data.

Avoid advertising SDKs, third-party trackers and unnecessary permissions.

Do not claim absolute anonymity or zero provider retention.

## 15. Visual design

Create a consistent visual identity across Android and iOS.

The keyboard should feel familiar, modern, spacious and premium.

Prioritize:

- Excellent Bengali typography.
- Clean, appropriately sized keys.
- Comfortable spacing and touch targets.
- Subtle animations.
- Light and dark appearance.
- Strong accessibility and readable contrast.
- A recognizable AI button.
- Compact communication-style selectors.
- Clear generation, error and offline states.

Do not overload the normal keyboard with AI controls.

The AI panel should open on demand and return seamlessly to normal typing.

Use a temporary product name and configurable branding. Do not permanently hardcode an unapproved brand identity.

## 16. Project organization

Maintain one repository with the following logical components:

- apps/android
- apps/ios
- backend
- shared
- intelligence
- infrastructure
- docs

Include:

- AGENTS.md
- README.md
- Environment variable example files
- Database migrations
- Build scripts
- Automated tests
- API contracts
- Privacy documentation
- Architecture documentation
- Device installation instructions

Keep the repository organized, but do not create unnecessary abstraction layers or empty modules.

## 17. Testing and acceptance criteria

Do not declare the product complete solely because the source code compiles.

Test:

- Android and iOS build success.
- Normal offline typing.
- Bengali phonetic conversion.
- Bengali Unicode composition and deletion.
- Keyboard switching.
- Text selection and replacement.
- Changes to drafts during AI generation.
- AI generation cancellation.
- Network interruption and recovery.
- Invalid and empty input.
- Sensitive input fields.
- Full Access disabled on iOS.
- Anonymous session handling.
- Daily usage limits.
- Concurrent requests and budget enforcement.
- API failures.
- Privacy and logging behavior.
- Light and dark themes.
- Keyboard responsiveness.
- AI quality in Bangla, Banglish and English.

Test compatibility with Messenger, WhatsApp, Instagram, Telegram, Gmail and other relevant applications where physically accessible.

Never report third-party application or physical-device compatibility as verified unless it was actually tested.

Benchmark AI quality based on naturalness, creativity, intention preservation, cultural relevance, repetition, response length, latency and cost.

Document which tests are automated and which require manual validation.

## 18. Execution sequence

Implement the project in the following order:

1. Inspect the development environment and establish the repository.
2. Create the shared AI backend and mock provider.
3. Implement and evaluate the Bangladeshi communication engine.
4. Build the functional Android keyboard.
5. Build the functional iOS keyboard.
6. Implement companion application interfaces.
7. Integrate personalization and free usage controls.
8. Run automated tests, builds and security checks.
9. Resolve discovered problems.
10. Prepare installable development builds and deployment instructions.

Continue through the stages without requesting unnecessary confirmation.

If blocked by credentials, physical devices, signing certificates or other external requirements, complete everything else that is possible.

Do not purchase services, register paid accounts, spend API funds without an authorized budget or deploy publicly without explicit approval.

## 19. Simplification review

Before adding any significant dependency, component or infrastructure, ask:

Do we really need that much stuff? If not, what is the simplest reliable implementation?

Review the final repository for:

- Unnecessary dependencies.
- Duplicate functionality.
- Overengineered architecture.
- Excessive code.
- Unused files.
- Security problems.
- Performance problems.
- Unnecessary network requests.

Simplify wherever possible without sacrificing functionality, privacy or reliability.

## 20. Final delivery

Deliver the actual working source code, build artifacts where possible, test results, setup instructions and a concise implementation report.

Clearly identify:

- Completed features.
- Verified builds.
- Passing and failing tests.
- Actual AI benchmark results.
- Remaining physical-device tests.
- External blockers.
- Exact steps required to install and run both keyboards.

Do not fabricate implementation results, successful tests or deployment status.

The immediate goal is a genuinely functional, free, Bangladesh-first AI keyboard on both Android and iOS.

Begin implementation now.