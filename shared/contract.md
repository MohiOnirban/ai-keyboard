# Keyboard v1
Android Kotlin IME and Compose companion, iOS UIKit extension and SwiftUI companion, Fastify TypeScript service, private PostgreSQL accounting. Anonymous HTTPS bearer token sessions. One configured backend base URL. Development port 4000. For emulator use 10.0.2.2, for simulator use localhost. Product name KothaKey.
POST /v1/sessions {} returns 201 {token:string}. GET /v1/usage with bearer returns {used:number,limit:number}. DELETE /v1/session revokes bearer and returns 204.
POST /v1/generate with bearer and Idempotency-Key UUID, JSON {mode:"rewrite"|"reply"|"express",text:string,style?:string,intensity?:"subtle"|"balanced"|"bold",relationship?:string,language?:"auto"|"bn"|"banglish"|"en",preferences?:{length?:string,humor?:string,formality?:string,emoji?:string,custom?:string}} returns 200 {suggestion:string,used:number,limit:number}. Non-2xx {error:string}, including 409 duplicate, 429 quota, 503 unavailable. Never persist response or draft server-side. Text max 1600 UTF-16 units.

## Keyboard appearance and navigation

| Role | Light | Dark |
| --- | --- | --- |
| Background | #F0F2EF | #121816 |
| Letter | #FFFFFF | #29312E |
| Function | #DCE9E2 | #354B42 |
| Accent | #006B58 | #006B58 |
| Text | #192923 | #F5F7F6 |
| Secondary | #75847D | #A4B3AC |

Use ten equal letter positions, half a position of inset on either side of the home row, and wider Shift and Backspace around the seven third-row letters. The spacebar identifies the active language. Typing, AI, clipboard and emoji share one height for a given device, orientation and user preference. System insets are measured separately. Keep the toolbar visible above a dedicated row of up to three local candidates. Suppress suggestions for protected fields. Each toolbar action has an accessible name.

The four AI tools share the generation endpoint. Grammar and translation use rewrite with the fixed styles `fix grammar` and `translation`. Translation sends the target via `language`. Tone sends the selected style. Ask AI uses the existing Rewrite, Reply and Express modes. Persist explicit permission for direct AI actions. Once granted, opening Grammar on a valid draft and choosing a tone can initiate a request. Other generation actions use their own explicit controls. Verify the original target before applying any result.

## Phase 2 typing behavior

The tools grid opens from the leftmost toolbar action and uses the typing area. Voice anchors the trailing side of the toolbar. Keep the alphabet’s ten-column width and its existing outer padding. The symbol layer has two pages. The dedicated keypad aligns three standard digit columns with stacked utility controls and uses text input actions for arithmetic characters.

Use the bundled language-tagged sentence corpus and lexical frequency data to predict offline. A loaded model can show typed text and ranked alternatives in three equal slots. Typo ranking may surface an option even where automatic correction is inappropriate. Keep the automatic edit policy conservative and the undo range anchored to the current editor. Preserve recognized dialect forms, names and sensitive editor content. The user can independently control next-word prediction, capitalization, correction, suggestions and local learning. Native implementations retain existing host input restrictions and platform dictation behavior.

## Acceptance

English, raw Banglish, phonetic Bangla and direct Bangla work offline. Preserve Unicode-safe editing, focus-change cancellation and sensitive-field exclusions. AI requires explicit preview and application. Sending remains a host-app action. The iOS extension can type with Full Access off. Keep the companion playground, private anonymous allowance and atomic caps. Validate both native builds with automated tests and record which workflows ran on a simulator or physical device. See [the master implementation](../docs/master.md).
