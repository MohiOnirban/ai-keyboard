# KothaKey iOS keyboard refinement

The iPhone reference supplied for this refinement takes precedence over the Android layout reference. The iOS layout follows the measured iPhone key proportions using KothaKey's green palette, own toolbar icons and iOS system typography. The Android implementation remains on its Phase 2 design path.

## Reference measurements

The three supplied typing images are 1,170 pixels wide. At 3× scale the width is 390 points. Each image begins the four key rows at y = 1,683, 1,845, 2,007 and 2,169 pixels. The flat three-slot suggestion strip begins at y = 1,524. Measurements use the visible key faces. The keyboard-only crops exclude the surrounding host conversation. [Measured keyboard crops](../artifacts/phase2/reference/ios/) · [Pixel scan](../artifacts/phase2/checks/ios-supplied-measurements.txt)

| Reference measurement | Pixels | Points at 3× |
| --- | ---: | ---: |
| Top-row regular face | 98–99 × 129 | 32.7–33 × 43 |
| Row pitch | 162 | 54 |
| Clear gap between faces | 32–33 | 10.7–11 |
| Horizontal gap between faces | 18 | 6 |
| Shift and Backspace widths | 133 | 44.3 |
| Spacebar width | 564 | 188 |
| Contextual action width | 273 | 91 |
| First key's left edge | 12 | 4 |

The Swift deck derives portrait row pitch from available width, using 162/1170. Key faces sit inside weighted touch cells. The home row begins half a column inward. Separate gutters surround Shift and Backspace, and a wider punctuation row uses five keys between its modifiers. Portrait space and return use roughly half and a quarter of the bottom row. Landscape compresses the vertical rhythm. The required AI tools toolbar adds a row above the native-inspired three-slot suggestion strip. The keyboard uses the existing input-view height for its AI, emoji and tools states.

## Native controls and platform behavior

The 123 and #+= pages use the supplied iPhone punctuation pattern. A number pad is available where iOS permits the extension and through the in-keyboard layout control. Tapping an emoji inserts it at the verified host caret. The return key uses the host trait and sends the newline/action through the document proxy. Host applications control how that action is interpreted.

Key previews stay inside the extension view. The keyboard offers long-press alternatives, Shift and Caps Lock, long-press deletion, and a press-and-drag spacebar that moves the cursor through the public document proxy. Input clicks use UIKit and respect the system setting. Haptic behavior depends on device support. The system owns its keyboard switch and Dictation controls on supported iPhones. The extension supplies a keyboard switch only where UIKit requests one. Its Voice tool hands off to system Dictation. [Custom keyboard interface](https://developer.apple.com/documentation/uikit/configuring-a-custom-keyboard-interface) · [Document proxy cursor API](https://developer.apple.com/documentation/uikit/uitextdocumentproxy/adjusttextposition(bycharacteroffset:)) · [Input-click feedback](https://developer.apple.com/documentation/uikit/uiinputviewaudiofeedback)

Apple controls secure-entry restrictions and can replace third-party keyboards in particular fields. iOS provides the extension only the portion of the document that the host exposes. KothaKey checks the selection and surrounding text before replacing. Existing suggestions, transliteration and local learning operate offline. AI sends text only through the existing request controls and requires iOS Full Access. [Apple keyboard extension restrictions](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html)

## Simulator verification

Both keyboard captures use the same editor on an iPhone 18 Pro simulator running iOS 27.2, at 1,206 × 2,622 pixels. KothaKey uses a 56-point row pitch and a 34-point letter width there. Apple measured approximately 55 and 33 points for the corresponding alphabet faces. Its symbol rows measured 54 points between key starts. KothaKey's additional toolbar accounts for the different total keyboard height. [Alphabet comparison](../artifacts/phase2/comparison/ios/alphabet.png) · [Symbols comparison](../artifacts/phase2/comparison/ios/symbols.png) · [Secondary symbols comparison](../artifacts/phase2/comparison/ios/secondary.png) · [Measurements](../artifacts/phase2/checks/ios-simulator-layout-measurements.txt)

Verification ran in stages on the actual extension. The earlier native pass checked all four AI panels against the local mock, with stable bounds, plus offline prediction, Bangla layouts, geometry, dark mode and Safari secure-field handoff. A follow-up passed five workflows, including learning and clearing locally stored words, and isolated a Caps Lock defect. The final installed code passed two focused regression tests covering Caps Lock, accented characters, repeat Backspace, emoji, Return, Spacebar cursor movement and prediction. The final ZIP also installed and launched. [Integrated native results](../artifacts/phase2/checks/ios-native-summary.json) · [Follow-up tests](../artifacts/phase2/checks/ios-native-final-summary.json) · [Final gesture and prediction tests](../artifacts/phase2/checks/ios-native-gesture-summary.json) · [Package launch](../artifacts/phase2/checks/ios-package-launch.txt)

The paired physical iPhone was unavailable. Dictation handoff was tested in the simulator. Speech transcription, audible clicks and haptic feel still need physical-device verification. The in-keyboard emoji picker has a curated grid with ABC return and Backspace. Emoji search and system-sized emoji categories remain open.
