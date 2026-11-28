import XCTest

final class CompanionTests: XCTestCase {
  override func setUp() {
    super.setUp()
    XCUIDevice.shared.orientation = .portrait
  }
  override func tearDown() {
    XCUIDevice.shared.orientation = .portrait
    super.tearDown()
  }

  func testOnboardingPracticeAndMockGeneration() throws {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    XCTAssertTrue(app.staticTexts["KothaKey"].waitForExistence(timeout: 10))
    app.buttons["Try the practice area"].tap()
    let input = app.textViews["Your message"]
    XCTAssertTrue(input.waitForExistence(timeout: 5))
    input.tap()
    if app.buttons["Fix Grammar"].waitForExistence(timeout: 2) {
      chooseLayout("English", in: app)
      for char in "meeting postponed today" {
        app.buttons[char == " " ? "Space" : String(char)].firstMatch.tap()
      }
    } else {
      input.typeText("meeting postponed today")
    }
    let generate = app.buttons["Generate suggestion"]
    let preview = app.buttons["Use suggestion"]
    let expired = app.staticTexts["Session expired. Try again."]
    generate.tap()
    let outcome = XCTNSPredicateExpectation(
      predicate: NSPredicate { _, _ in preview.exists || expired.exists }, object: app)
    XCTAssertEqual(XCTWaiter().wait(for: [outcome], timeout: 25), .completed, app.debugDescription)
    if !preview.exists && expired.exists {
      generate.tap()
    }
    XCTAssertTrue(preview.waitForExistence(timeout: 25), app.debugDescription)
    preview.tap()
    XCTAssertFalse((input.value as? String ?? "").isEmpty)
    app.tabBars.buttons["Settings"].tap()
    let allowance = app.buttons["Check daily allowance"]
    for _ in 0..<4 where !allowance.isHittable { app.swipeUp() }
    allowance.tap()
    XCTAssertTrue(
      app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "generations used today"))
        .firstMatch.waitForExistence(timeout: 8))
    let reset = app.buttons["Reset anonymous session"]
    if !reset.isHittable { app.swipeUp() }
    reset.tap()
    XCTAssertTrue(app.staticTexts["Session reset."].waitForExistence(timeout: 8))
    if !allowance.isHittable { app.swipeDown() }
    allowance.tap()
    XCTAssertTrue(
      app.staticTexts.containing(
        NSPredicate(
          format: "label BEGINSWITH %@ AND label ENDSWITH %@", "0 of ", " generations used today."
        )
      ).firstMatch.waitForExistence(timeout: 8))
  }

  func testSystemKeyboardOfflineTyping() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
    let input = showExtension(in: app)
    let ai = app.buttons["Fix Grammar"]
    ai.tap()
    XCTAssertTrue(app.staticTexts["Allow Full Access to use AI."].exists)
    app.buttons["Return to keyboard"].tap()
    chooseLayout("Phonetic", in: app)
    app.buttons["Shift"].tap()
    app.buttons["A"].tap()
    app.buttons["x"].tap()
    app.buttons["Backspace"].tap()
    app.buttons["m"].tap()
    app.buttons["i"].tap()
    app.buttons["Space"].tap()
    for word in ["tomake", "bhalobashi"] {
      for letter in word { app.buttons[String(letter)].firstMatch.tap() }
      app.buttons["Space"].tap()
    }
    XCTAssertEqual(input.value as? String, "আমি তোমাকে ভালোবাসি ")
  }

  func testSystemKeyboardMockGeneration() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: true)
    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    for letter in "meeting" { app.buttons[String(letter)].firstMatch.tap() }
    app.buttons["Space"].tap()
    app.buttons["Fix Grammar"].tap()
    startIfNeeded(in: app)
    let insert = app.buttons["Insert"]
    XCTAssertTrue(insert.waitForExistence(timeout: 25), app.debugDescription)
    XCTAssertEqual(input.value as? String, "meeting ")
    insert.tap()
    XCTAssertTrue((input.value as? String)?.hasPrefix("meeting ") == true)
    XCTAssertNotEqual(input.value as? String, "meeting ")
  }

  func testSafariLocalFixtureOfflineTyping() throws {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)

    let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
    safari.open(URL(string: "http://127.0.0.1:4100/SafariFixture.html")!)
    let welcome = safari.buttons["Continue"]
    if welcome.exists { welcome.tap() }
    let title = safari.staticTexts["Kotha Keys local keyboard fixture"]
    try XCTSkipUnless(
      title.waitForExistence(timeout: 10),
      "Start the local Safari fixture server described in the iOS README.")

    XCUIDevice.shared.orientation = .landscapeLeft
    defer { XCUIDevice.shared.orientation = .portrait }
    XCTAssertTrue(safari.frame.width > safari.frame.height)
    let input = safari.descendants(matching: .any).matching(
      NSPredicate(format: "label == %@", "Synthetic message")
    ).firstMatch
    XCTAssertTrue(input.waitForExistence(timeout: 6), safari.debugDescription)
    input.tap()
    selectExtension(in: safari)
    chooseLayout("Phonetic", in: safari)
    for letter in "ami" { safari.buttons[String(letter)].firstMatch.tap() }
    safari.buttons["Space"].tap()
    XCTAssertEqual(input.value as? String, "আমি ")
    let evidence = XCTAttachment(screenshot: safari.screenshot())
    evidence.name = "Safari offline Bengali typing"
    evidence.lifetime = .keepAlways
    add(evidence)
    XCUIDevice.shared.orientation = .portrait
    let numeric = safari.textFields["Numeric field"]
    numeric.tap()
    // WebKit may deliver the default trait while switching directly between HTML fields.
    // A fresh activation on the same number field below exercises the numeric trait.
    if safari.buttons["0"].waitForExistence(timeout: 2) { shot("11-safari-numeric-field", safari) }
    let secure = safari.secureTextFields["Password field"]
    secure.tap()
    let hidden = XCTNSPredicateExpectation(
      predicate: NSPredicate(format: "exists == false"), object: safari.buttons["Fix Grammar"])
    XCTAssertEqual(XCTWaiter().wait(for: [hidden], timeout: 5), .completed)
    shot("12-safari-secure-system-keyboard", safari)
    // Re-entering from the protected system keyboard independently verifies the host's numeric trait.
    numeric.tap()
    let active = safari.buttons["Fix Grammar"]
    XCTAssertTrue(active.waitForExistence(timeout: 6), safari.debugDescription)
    XCTAssertTrue(safari.buttons["0"].waitForExistence(timeout: 6), safari.debugDescription)
    XCTAssertFalse(safari.buttons["q"].exists)
    shot("11-safari-numeric-field", safari)

  }

  func testSystemKeyboardEditedDraftAndCancel() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: true)
    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    for letter in "meeting" { app.buttons[String(letter)].firstMatch.tap() }
    app.buttons["Space"].tap()
    app.buttons["Fix Grammar"].tap()
    startIfNeeded(in: app)
    let insert = app.buttons["Insert"]
    XCTAssertTrue(insert.waitForExistence(timeout: 20), app.debugDescription)
    input.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.1)).tap()
    XCTAssertTrue(
      app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "Draft changed"))
        .firstMatch.waitForExistence(timeout: 5))
    XCTAssertFalse(insert.exists)
    XCTAssertEqual(input.value as? String, "meeting ")
    app.buttons["Return to keyboard"].tap()
    XCTAssertFalse(insert.exists)
    XCTAssertTrue(app.buttons["Fix Grammar"].exists)
    XCTAssertEqual(input.value as? String, "meeting ")

    app.terminate()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
  }

  private func shot(_ name: String, _ app: XCUIApplication) {
    let item = XCTAttachment(screenshot: app.screenshot())
    item.name = "ios-after-" + name
    item.lifetime = .keepAlways
    add(item)
  }

  func testPhaseOneLayoutsAndGeometry() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    let q = app.buttons["q"].frame
    let a = app.buttons["a"].frame
    let z = app.buttons["z"].frame
    XCTAssertEqual(q.width, a.width, accuracy: 1)
    XCTAssertEqual(a.width, z.width, accuracy: 1)
    XCTAssertEqual(a.minX - q.minX, q.width / 2, accuracy: 1.5)
    XCTAssertEqual(z.minX - q.minX, q.width * 1.5, accuracy: 1.5)
    XCTAssertGreaterThan(app.buttons["Shift"].frame.width, q.width)
    XCTAssertGreaterThan(app.buttons["Backspace"].frame.width, q.width)
    XCTAssertEqual(app.buttons["Space"].frame.width, q.width * 5, accuracy: 1)
    XCTAssertEqual(app.buttons["Return"].frame.width, q.width * 2.5, accuracy: 1)
    XCTAssertTrue(app.buttons["emoji-key"].exists)
    print("KOTHA_GEOMETRY q=\(q) a=\(a) z=\(z) space=\(app.buttons["Space"].frame)")
    shot("01-english-light", app)
    app.buttons["q"].press(forDuration: 0.7)
    XCTAssertEqual(input.value as? String, "1")
    app.buttons["123"].tap()
    shot("02-symbols", app)
    app.buttons["#+="].tap()
    shot("03-secondary-symbols", app)
    app.buttons["123"].tap()
    XCTAssertTrue(app.buttons["£"].exists)
    openNumeric(in: app)
    shot("04-numeric", app)
    XCTAssertEqual(app.buttons["0"].frame.midX, app.buttons["2"].frame.midX, accuracy: 1)
    XCTAssertEqual(app.buttons["1"].frame.width, app.buttons["2"].frame.width, accuracy: 1)
    XCTAssertEqual(app.buttons["2"].frame.width, app.buttons["3"].frame.width, accuracy: 1)
    XCTAssertEqual(app.buttons["0"].frame.height, q.height, accuracy: 1)
    XCTAssertEqual(app.buttons["Backspace"].frame.midY, app.buttons["0"].frame.midY, accuracy: 1)
    app.buttons["0"].tap()
    app.buttons["Tools"].tap()
    XCTAssertTrue(app.buttons["Theme"].exists)
    XCTAssertTrue(app.buttons["Clipboard"].exists)
    XCTAssertTrue(app.buttons["Emoji"].exists)
    XCTAssertTrue(app.buttons["Voice input"].exists)
    XCTAssertEqual(app.buttons["Theme"].frame.minY, app.buttons["Clipboard"].frame.minY, accuracy: 1)
    XCTAssertEqual(app.buttons["Theme"].frame.minX, app.buttons["Emoji"].frame.minX, accuracy: 1)
    shot("phase2-tools-dashboard", app)
    app.buttons["Close tools"].tap()
    XCTAssertTrue(app.buttons["0"].exists)
    app.buttons["ABC"].tap()
    chooseLayout("বাংলা", in: app)
    shot("05-direct-bangla", app)
    app.buttons["ক"].tap()
    app.buttons["Shift"].tap()
    app.buttons["া"].tap()
    XCTAssertEqual(input.value as? String, "10কা")
    chooseLayout("Phonetic", in: app)
    for ch in "ami" { app.buttons[String(ch)].firstMatch.tap() }
    shot("06-phonetic-candidates", app)
    app.buttons["Space"].tap()
    XCTAssertEqual(input.value as? String, "10কাআমি ")
    chooseLayout("Banglish", in: app)
    for ch in "am" { app.buttons[String(ch)].firstMatch.tap() }
    shot("07-banglish-suggestions", app)
    let word = app.buttons["amar"]
    XCTAssertTrue(word.exists)
    word.tap()
    XCTAssertEqual(input.value as? String, "10কাআমি amar ")
    captureLandscape(in: app)
  }

  private func captureLandscape(in app: XCUIApplication) {
    XCUIDevice.shared.orientation = .landscapeLeft
    let resized = NSPredicate { _, _ in
      let window = app.frame
      let letter = app.buttons["q"].frame
      let delete = app.buttons["Backspace"].frame
      return window.width > window.height && letter.width > 60 && window.contains(delete)
        && delete.maxX > window.width * 0.75
    }
    let settled = XCTNSPredicateExpectation(predicate: resized, object: app)
    XCTAssertEqual(XCTWaiter().wait(for: [settled], timeout: 12), .completed, app.debugDescription)
    // Geometry updates before the system compositor's rotation animation ends.
    RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.85))
    let frame = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
    frame.name = "ios-after-08-landscape"
    frame.lifetime = .keepAlways
    add(frame)
    XCUIDevice.shared.orientation = .portrait
    let upright = XCTNSPredicateExpectation(
      predicate: NSPredicate { _, _ in
        app.frame.height > app.frame.width && app.buttons["q"].frame.width < 55
      }, object: app)
    XCTAssertEqual(XCTWaiter().wait(for: [upright], timeout: 10), .completed)
  }

  func testPhaseOneAssistantAndUtilityStates() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: true)
    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    for ch in "im on teh bus" { app.buttons[ch == " " ? "Space" : String(ch)].firstMatch.tap() }
    typeOnKotha(".", in: app)
    let draft = input.value as? String
    let top = app.buttons["Fix Grammar"].frame.minY
    let bottom = app.buttons["Space"].frame.maxY
    for (i, label) in ["Fix Grammar", "Improve & Tone", "Ask AI", "Translation"].enumerated() {
      app.buttons[label].tap()
      let back = app.buttons["Return to keyboard"]
      XCTAssertTrue(back.exists)
      XCTAssertFalse(app.buttons["q"].exists)
      XCTAssertEqual(back.frame.minY, top, accuracy: 1)
      let submit = generationButton(in: app)
      if submit.exists {
        XCTAssertEqual(submit.frame.maxY, bottom, accuracy: 1)
        shot("ai-\(i)-input", app)
      }
      if label == "Translation" {
        app.buttons["Edit input"].tap()
        for _ in (draft ?? "") { app.buttons["Backspace"].tap() }
        for ch in "How are you" {
          if ch == "H" { app.buttons["Shift"].tap() }
          app.buttons[ch == " " ? "Space" : String(ch)].firstMatch.tap()
        }
        app.buttons["123"].tap()
        app.buttons["?"].tap()
        app.buttons["Done"].tap()
      }
      startIfNeeded(in: app)
      let generating = app.staticTexts["Generating…"]
      if generating.waitForExistence(timeout: 2) {
        XCTAssertEqual(app.buttons["Cancel"].frame.maxY, bottom, accuracy: 1)
        XCTAssertEqual(back.frame.minY, top, accuracy: 1)
        shot("ai-\(i)-loading", app)
      }
      let insert = app.buttons["Insert"]
      XCTAssertTrue(insert.waitForExistence(timeout: 25), app.debugDescription)
      XCTAssertEqual(input.value as? String, draft)
      XCTAssertEqual(insert.frame.maxY, bottom, accuracy: 1)
      shot("ai-\(i)-result", app)
      back.tap()
      XCTAssertTrue(app.buttons["q"].exists)
      XCTAssertEqual(input.value as? String, draft)
    }
    app.buttons["emoji-key"].tap()
    shot("utility-emoji", app)
    app.buttons["Return to keyboard"].tap()
    app.buttons["Clipboard"].tap()
    shot("utility-clipboard", app)
    app.buttons["Return to keyboard"].tap()
    app.buttons["Tools"].tap()
    shot("utility-more", app)
    app.buttons["Close tools"].tap()
    print("KOTHA_STABLE top=\(top) bottom=\(bottom) across all four tools")
  }

  func testSelectedPassageAndCanceledRequest() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: true)
    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    for c in "teh." { app.buttons[String(c)].firstMatch.tap() }
    input.coordinate(withNormalizedOffset: CGVector(dx: 0.04, dy: 0.12)).doubleTap()
    app.buttons["Fix Grammar"].tap()
    if generationButton(in: app).exists {
      XCTAssertTrue(
        app.staticTexts.containing(NSPredicate(format: "label BEGINSWITH %@", "Selected passage"))
          .firstMatch.exists, app.debugDescription)
      startIfNeeded(in: app)
    }
    if app.staticTexts["Generating…"].waitForExistence(timeout: 2) {
      app.buttons["Cancel"].tap()
      XCTAssertEqual(input.value as? String, "teh.")
      app.buttons["Fix Grammar"].tap()
      startIfNeeded(in: app)
    }
    let replace = app.buttons["Replace Text"]
    XCTAssertTrue(replace.waitForExistence(timeout: 20), app.debugDescription)
    XCTAssertEqual(input.value as? String, "teh.")
    shot("13-selected-passage-result", app)
    replace.tap()
    XCTAssertNotEqual(input.value as? String, "teh.")
    XCTAssertTrue((input.value as? String)?.hasSuffix(".") == true)
  }

  func testToneAlternativesAndPrivateInstructions() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: true)
    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    openNumeric(in: app)
    XCTAssertEqual(app.buttons["0"].frame.midX, app.buttons["2"].frame.midX, accuracy: 1)
    shot("04-numeric", app)
    app.buttons["ABC"].tap()
    app.buttons["123"].tap()
    XCTAssertTrue(app.buttons["@"].exists)
    app.buttons["ABC"].tap()
    captureLandscape(in: app)
    typeOnKotha("i like you.", in: app)
    let draft = input.value as? String
    app.buttons["Improve & Tone"].tap()
    startIfNeeded(in: app)
    XCTAssertTrue(app.buttons["Insert"].waitForExistence(timeout: 20))
    let strip = app.scrollViews.containing(.button, identifier: "My Style").firstMatch
    let romantic = app.buttons["Romantic"].firstMatch
    if !romantic.isHittable { strip.swipeLeft() }
    romantic.tap()
    let firstCard = app.buttons.matching(
      NSPredicate(format: "label BEGINSWITH %@", "Alternative 1,")
    ).firstMatch
    XCTAssertTrue(firstCard.waitForExistence(timeout: 20), app.debugDescription)
    firstCard.tap()
    shot("14-tone-alternatives", app)
    XCTAssertEqual(input.value as? String, draft)
    app.buttons["Return to keyboard"].tap()
    app.buttons["Ask AI"].tap()
    app.buttons["Instructions"].tap()
    for c in "short" { app.buttons[String(c)].firstMatch.tap() }
    XCTAssertEqual(input.value as? String, draft)
    app.buttons["Done"].tap()
    app.buttons["Instructions"].tap()
    XCTAssertTrue(app.staticTexts["short"].exists)
    app.buttons["Done"].tap()
    app.buttons["Return to keyboard"].tap()
    XCTAssertEqual(input.value as? String, draft)
  }

  func testPhaseOneDarkTheme() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: true)
    app.tabBars.buttons["Settings"].tap()
    let appearance = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Appearance"))
      .firstMatch
    for _ in 0..<5 where !appearance.isHittable { app.swipeUp() }
    appearance.tap()
    app.buttons["Dark"].firstMatch.tap()
    app.tabBars.buttons["Practice"].tap()
    let input = app.textViews["Your message"]
    input.tap()
    selectExtension(in: app)
    chooseLayout("English", in: app)
    shot("09-english-dark", app)
    app.buttons["Fix Grammar"].tap()
    shot("10-grammar-dark", app)
    app.buttons["Return to keyboard"].tap()
    app.terminate()
    app.launch()
    app.tabBars.buttons["Settings"].tap()
    for _ in 0..<5 where !appearance.isHittable { app.swipeUp() }
    appearance.tap()
    app.buttons["System"].firstMatch.tap()
  }

  func testPhaseElevenOfflinePredictionsAutocorrectAndDictionary() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
    app.tabBars.buttons["Settings"].tap()
    configureAutocorrect(false, in: app)

    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    app.buttons["Tools"].tap()
    app.buttons["dashboard-settings"].tap()
    for title in ["Predictive suggestions", "Next-word prediction"] {
      let setting = app.buttons[title]
      if setting.value as? String == "Off" { setting.tap() }
    }
    app.buttons["Return to keyboard"].tap()
    let first = app.buttons["candidate-0"]
    XCTAssertTrue(first.exists)
    XCTAssertGreaterThan(first.frame.minY, app.buttons["Fix Grammar"].frame.minY)
    XCTAssertLessThan(first.frame.maxY, app.buttons["q"].frame.minY)
    typeOnKotha("teh", in: app)
    XCTAssertTrue(app.buttons["the"].exists, app.debugDescription)
    XCTAssertTrue(app.buttons["Fix Grammar"].exists)
    shot("phase11-english-correction", app)
    app.buttons["the"].tap()
    XCTAssertEqual(input.value as? String, "the ")
    typeOnKotha("thank ", in: app)
    XCTAssertTrue(app.buttons["you"].exists, app.debugDescription)
    app.buttons["you"].tap()
    XCTAssertEqual(input.value as? String, "the thank you ")
    app.buttons["Tools"].tap()
    app.buttons["Voice input"].tap()
    XCTAssertTrue(app.buttons["Switch keyboard"].exists)
    shot("phase11-dictation", app)
    app.buttons["Switch keyboard"].tap()
    XCTAssertTrue(app.buttons["Fix Grammar"].waitForNonExistence(timeout: 5))

    app.terminate()
    app.launch()
    app.tabBars.buttons["Settings"].tap()
    configureAutocorrect(true, in: app)
    let dictionary = app.buttons["Personal dictionary"]
    tapAfterScrolling(dictionary, in: app)
    let entry = app.textFields["Add a word"]
    entry.tap()
    if app.buttons["Fix Grammar"].exists {
      chooseLayout("English", in: app)
      typeOnKotha("zanovia", in: app)
    } else {
      entry.typeText("zanovia")
    }
    app.buttons["Save word"].tap()
    XCTAssertTrue(app.staticTexts["zanovia"].exists)

    _ = showExtension(in: app)
    chooseLayout("English", in: app)
    let previous = input.value as? String ?? ""
    typeOnKotha("teh ", in: app)
    XCTAssertEqual(input.value as? String, previous + "the ")
    let undo = app.buttons["Undo · teh"]
    XCTAssertTrue(undo.exists, app.debugDescription)
    shot("phase11-undo", app)
    undo.tap()
    XCTAssertEqual(input.value as? String, previous + "teh ")
    typeOnKotha("zan", in: app)
    XCTAssertTrue(app.buttons["zanovia"].exists, app.debugDescription)
    app.buttons["zanovia"].tap()
    XCTAssertEqual(input.value as? String, previous + "teh zanovia ")

    app.terminate()
    app.launch()
    app.tabBars.buttons["Settings"].tap()
    let saved = app.staticTexts["zanovia"]
    tapAfterScrolling(app.buttons["Personal dictionary"], in: app)
    saved.swipeLeft()
    let remove = app.buttons["Delete"].firstMatch
    if remove.exists { remove.tap() }
    XCTAssertFalse(saved.exists)
    app.navigationBars["Personal dictionary"].buttons["Settings"].tap()
    configureAutocorrect(false, in: app)
  }

  func testPhaseTwoPredictionAndTools() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
    let input = showExtension(in: app)
    chooseLayout("English", in: app)

    let tools = app.buttons["Tools"]
    XCTAssertLessThan(tools.frame.midX, app.buttons["Fix Grammar"].frame.midX)
    let microphone = app.buttons["toolbar-Voice input"]
    if microphone.exists {
      XCTAssertGreaterThan(microphone.frame.midX, app.buttons["Translation"].frame.midX)
    }
    tools.tap()
    app.buttons["dashboard-settings"].tap()
    let predictive = app.buttons["Predictive suggestions"]
    if predictive.value as? String == "Off" { predictive.tap() }
    let next = app.buttons["Next-word prediction"]
    if next.value as? String == "On" { next.tap() }
    let autocorrect = app.buttons["Autocorrect"]
    if autocorrect.value as? String == "On" { autocorrect.tap() }
    let caps = app.buttons["Auto-capitalization"]
    if caps.value as? String == "On" { caps.tap() }
    app.buttons["Return to keyboard"].tap()
    let firstSlot = app.buttons["candidate-0"]
    XCTAssertTrue(!firstSlot.exists || !firstSlot.isEnabled)
    typeOnKotha("hey how ya doing", in: app)
    app.buttons["123"].tap()
    app.buttons["?"].tap()
    app.buttons["ABC"].tap()
    typeOnKotha(" im", in: app)
    let contraction = app.buttons["I'm"]
    XCTAssertTrue(contraction.waitForExistence(timeout: 5), app.debugDescription)
    XCTAssertEqual(app.buttons["candidate-0"].label, "I'm")
    XCTAssertEqual(app.buttons["candidate-1"].label, "im")
    shot("phase2-contraction-suggestions", app)
    contraction.tap()
    XCTAssertEqual(input.value as? String, "hey how ya doing? I'm ")
    XCTAssertEqual(firstSlot.label, "Undo · im")

    let space = app.buttons["Space"]
    let grip = space.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
    grip.press(forDuration: 0.62, thenDragTo: grip.withOffset(CGVector(dx: -18, dy: 0)))
    typeOnKotha(",", in: app)
    XCTAssertEqual(input.value as? String, "hey how ya doing? I'm, ")

    openNumeric(in: app)
    typeOnKotha("123", in: app)
    XCTAssertEqual(input.value as? String, "hey how ya doing? I'm,123 ")
    app.buttons["ABC"].tap()

    tools.tap()
    app.buttons["dashboard-settings"].tap()
    next.tap()
    caps.tap()
    app.buttons["Return to keyboard"].tap()
    typeOnKotha(".", in: app)
    app.buttons["Space"].tap()
    XCTAssertTrue(app.buttons["H"].exists)
    app.buttons["H"].tap()
    XCTAssertTrue((input.value as? String)?.contains(". H") == true)
    tools.tap()
    app.buttons["dashboard-settings"].tap()
    caps.tap()
    app.buttons["Return to keyboard"].tap()
  }

  func testNativeAppleSameEditorComparison() throws {
    #if !targetEnvironment(simulator)
      throw XCTSkip("Capture comparisons on the simulator")
    #endif
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
    _ = showExtension(in: app)
    chooseLayout("English", in: app)
    XCTAssertTrue(app.buttons["Tools"].exists)
    app.buttons["Shift"].tap()
    shot("compare-kotha-alphabet", app)
    app.buttons["123"].tap()
    shot("compare-kotha-symbols", app)
    app.buttons["#+="].tap()
    shot("compare-kotha-secondary", app)
    app.buttons["ABC"].tap()

    let globe = nextKeyboard(in: app)
    XCTAssertTrue(globe.exists)
    globe.tap()
    let alphabet = app.keys.matching(NSPredicate(format: "label =[c] %@", "q")).firstMatch
    for _ in 0..<5 where !alphabet.waitForExistence(timeout: 1) {
      if globe.isHittable { globe.tap() }
    }
    XCTAssertTrue(alphabet.waitForExistence(timeout: 5), app.debugDescription)
    shot("compare-apple-alphabet", app)

    let nativeKeyboard = app.keyboards.firstMatch
    let space = nativeKeyboard.keys.matching(NSPredicate(format: "label =[c] %@", "space")).firstMatch
    XCTAssertTrue(space.isHittable, app.debugDescription)
    // The system numbers key occupies the first position in the spacebar's row.
    let keyboardFrame = nativeKeyboard.frame
    let nativeNumbers = app.coordinate(withNormalizedOffset: .zero).withOffset(
      CGVector(dx: keyboardFrame.minX - app.frame.minX + keyboardFrame.width * 0.065,
        dy: space.frame.midY - app.frame.minY))
    nativeNumbers.tap()
    XCTAssertTrue(nativeKeyboard.keys["1"].waitForExistence(timeout: 5), app.debugDescription)
    shot("compare-apple-symbols", app)

    let question = nativeKeyboard.keys["?"]
    XCTAssertTrue(question.isHittable, app.debugDescription)
    let nativeMoreSymbols = app.coordinate(withNormalizedOffset: .zero).withOffset(
      CGVector(dx: keyboardFrame.minX - app.frame.minX + keyboardFrame.width * 0.065,
        dy: question.frame.midY - app.frame.minY))
    nativeMoreSymbols.tap()
    XCTAssertTrue(nativeKeyboard.keys["["].waitForExistence(timeout: 5), app.debugDescription)
    shot("compare-apple-secondary", app)
    selectExtension(in: app)
  }


  func testNativeKeypressGesturesAndEmoji() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
    let input = showExtension(in: app)
    chooseLayout("English", in: app)

    app.buttons["e"].press(forDuration: 0.7)
    let accent = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "é")).firstMatch
    XCTAssertTrue(accent.waitForExistence(timeout: 5), app.debugDescription)
    accent.tap()
    XCTAssertEqual(input.value as? String, "é")
    let shift = app.buttons["Shift"]
    shift.doubleTap()
    XCTAssertEqual(shift.value as? String, "Caps Lock")
    app.buttons["H"].tap()
    app.buttons["I"].tap()
    XCTAssertEqual(input.value as? String, "éHI")
    app.buttons["Shift"].tap()
    app.buttons["Backspace"].press(forDuration: 0.88)
    XCTAssertLessThan((input.value as? String ?? "").count, 3)

    app.buttons["emoji-key"].tap()
    let emoji = app.buttons["🙂"]
    XCTAssertTrue(emoji.exists)
    emoji.tap()
    XCTAssertTrue((input.value as? String)?.contains("🙂") == true)
    shot("native-emoji", app)
    app.buttons["ABC"].tap()
    XCTAssertTrue(app.buttons["q"].exists)
    app.buttons["Return"].tap()
    XCTAssertTrue((input.value as? String)?.hasSuffix("\n") == true)
  }

  func testPhaseTwoLearningAndClear() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: true)
    app.tabBars.buttons["Settings"].tap()
    let learning = app.switches["Learn from typing"]
    setPreference(learning, enabled: true, in: app)
    clearLearnedWords(in: app)

    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    typeOnKotha("zaqv zaqv za", in: app)
    XCTAssertEqual(input.value as? String, "zaqv zaqv za")
    let learned = app.buttons["zaqv"]
    XCTAssertTrue(learned.waitForExistence(timeout: 5), app.debugDescription)
    learned.tap()
    XCTAssertEqual(input.value as? String, "zaqv zaqv zaqv ")

    app.terminate()
    app.launch()
    app.tabBars.buttons["Settings"].tap()
    clearLearnedWords(in: app)
    _ = showExtension(in: app)
    typeOnKotha("za", in: app)
    let available = XCTNSPredicateExpectation(
      predicate: NSPredicate { _, _ in app.buttons["candidate-0"].isEnabled }, object: app)
    XCTAssertEqual(XCTWaiter().wait(for: [available], timeout: 5), .completed)
    XCTAssertFalse(learned.exists)
    app.terminate()
    app.launch()
    app.tabBars.buttons["Settings"].tap()
    setPreference(learning, enabled: false, in: app)
  }

  func testAdvancedCursorReplacementUndoAndSpacing() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    app.buttons["Tools"].tap()
    app.buttons["dashboard-settings"].tap()
    for (title, state) in [("Predictive suggestions","On"), ("Auto-capitalization","Off"), ("Autocorrect","Off")] {
      let setting = app.buttons[title]
      if setting.value as? String != state { setting.tap() }
    }
    app.buttons["Return to keyboard"].tap()
    typeOnKotha("say teh ok", in: app)
    app.buttons["Tools"].tap()
    app.buttons["Text editing"].tap()
    let left = app.buttons["Move cursor left"]
    for _ in 0..<4 { left.tap() }
    app.buttons["Return to keyboard"].tap()
    let corrected = app.buttons["the"]
    XCTAssertTrue(corrected.waitForExistence(timeout: 4), app.debugDescription)
    shot("advanced-cursor-suggestions", app)
    corrected.tap()
    XCTAssertEqual(input.value as? String, "say the ok")
    app.buttons["Undo · teh"].tap()
    XCTAssertEqual(input.value as? String, "say teh ok")
    shot("advanced-cursor-undo", app)

    // Move to the end of the existing sentence and complete a new word.
    app.buttons["Tools"].tap()
    app.buttons["Text editing"].tap()
    for _ in 0..<3 { app.buttons["Move cursor right"].tap() }
    app.buttons["Return to keyboard"].tap()
    typeOnKotha(" teh", in: app)
    corrected.tap()
    XCTAssertEqual(input.value as? String, "say teh ok the ")
    typeOnKotha(",", in: app)
    XCTAssertEqual(input.value as? String, "say teh ok the,")
    typeOnKotha(" yes  ", in: app)
    XCTAssertEqual(input.value as? String, "say teh ok the, yes. ")
    shot("advanced-spacing", app)
  }

  func testAdvancedSafariSelectionAndProtectedFields() throws {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
    let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
    safari.open(URL(string: "http://127.0.0.1:4100/SafariFixture.html")!)
    if safari.buttons["Continue"].exists { safari.buttons["Continue"].tap() }
    let select = safari.buttons["Select misspelling"]
    try XCTSkipUnless(select.waitForExistence(timeout: 8), "Start the local Safari fixture server.")
    select.tap()
    selectExtension(in: safari)
    let candidate = safari.buttons["the"]
    XCTAssertTrue(candidate.waitForExistence(timeout: 5), safari.debugDescription)
    candidate.tap()
    let input = safari.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Synthetic message")).firstMatch
    XCTAssertEqual(input.value as? String, "Draft one.\nPlease check the message.")
    let undo = safari.buttons["Undo · teh"]
    XCTAssertTrue(undo.waitForExistence(timeout: 3))
    undo.tap()
    XCTAssertEqual(input.value as? String, "Draft one.\nPlease check teh message.")
    safari.buttons["Place cursor after misspelling"].tap()
    XCTAssertEqual(input.value as? String, "Draft one.\nPlease check teh message.")
    let refreshed = safari.buttons.matching(NSPredicate(format: "label == %@", "the")).firstMatch
    XCTAssertTrue(refreshed.waitForExistence(timeout: 4), safari.debugDescription)
    XCTAssertEqual(refreshed.label, "the")
    refreshed.tap()
    XCTAssertEqual(input.value as? String, "Draft one.\nPlease check the message.", safari.debugDescription)
    // Tap the earlier line inside the real editor, as a user would.
    input.coordinate(withNormalizedOffset: CGVector(dx: 0.15, dy: 0.19)).tap()
    let stale = safari.buttons.matching(NSPredicate(format: "label == %@", "Undo · teh")).firstMatch
    XCTAssertTrue(stale.waitForNonExistence(timeout: 3), "Moving the host caret should dismiss the previous Undo.")
    XCTAssertEqual(input.value as? String, "Draft one.\nPlease check the message.")
    shot("advanced-safari-multiline", safari)
    for field in ["Email field", "URL field"] {
      let kind = field == "Email field" ? "email" : "url"
      safari.open(URL(string: "http://127.0.0.1:4100/SafariFixture.html?field=" + kind)!)
      let editor = safari.textFields[field]
      XCTAssertTrue(editor.waitForExistence(timeout: 6))
      editor.tap()
      let focused = safari.descendants(matching: .any).matching(NSPredicate(format: "label == %@ AND hasKeyboardFocus == 1", field)).firstMatch
      if !focused.waitForExistence(timeout: 2) { editor.tap() }
      XCTAssertTrue(focused.waitForExistence(timeout: 4), editor.debugDescription)
      // Safari can replace the extension with its own keyboard for contact input.
      let toolbar = safari.buttons["Fix Grammar"]
      let key = safari.buttons.matching(NSPredicate(format: "label ==[c] %@", "t")).firstMatch
      if toolbar.exists && key.exists {
        for letter in "teh" {
          safari.buttons.matching(NSPredicate(format: "label ==[c] %@", String(letter))).firstMatch.tap()
        }
      } else {
        editor.typeText("teh")
      }
      XCTAssertEqual(editor.value as? String, "teh", field)
      let slot = safari.buttons["candidate-0"]
      XCTAssertTrue(!slot.exists || !slot.isEnabled, "Host field: \(field); candidate: \(slot.exists ? slot.label : "")")
      shot("advanced-safari-" + (field == "Email field" ? "email" : "url"), safari)
    }

  }

  func testAdvancedGlideAndBackspaceUndo() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    let start = app.buttons["w"].firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
    let end = app.buttons["t"].firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
    start.press(forDuration: 0.02, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 0)
    let undo = app.buttons["Undo · glide"]
    XCTAssertTrue(undo.waitForExistence(timeout: 5), app.debugDescription)
    let inserted = input.value as? String ?? ""
    XCTAssertEqual(inserted.trimmingCharacters(in: .whitespaces).lowercased(), "wet")
    shot("advanced-glide-candidates", app)
    undo.tap()
    XCTAssertTrue((input.value as? String ?? "").isEmpty)
    typeOnKotha("wet", in: app)
    XCTAssertEqual(input.value as? String, "wet")
  }

  func testAdvancedClipboardCutAndPinnedHistory() throws {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: true)
    app.tabBars.buttons["Settings"].tap()
    let remember = app.switches["Remember clipboard items"]
    setPreference(remember, enabled: false, in: app)
    setPreference(remember, enabled: true, in: app)
    // A native UITextView exposes a stable selection around this measured edit.
    let native = showExtension(in: app)
    chooseLayout("English", in: app)
    app.buttons["Tools"].tap()
    app.buttons["dashboard-settings"].tap()
    let capitalization = app.buttons["Auto-capitalization"]
    if capitalization.value as? String == "On" { capitalization.tap() }
    app.buttons["Return to keyboard"].tap()
    typeOnKotha("word.", in: app)
    let draft = native.value as? String ?? ""
    XCTAssertEqual(draft.lowercased(), "word.")
    native.coordinate(withNormalizedOffset: CGVector(dx: 0.04, dy: 0.12)).doubleTap()
    app.buttons["Tools"].tap()
    app.buttons["Clipboard"].tap()
    let cut = app.buttons["Cut"]
    XCTAssertTrue(cut.isEnabled)
    cut.tap()
    XCTAssertEqual(native.value as? String, ".")
    let undo = app.buttons["Undo · cut"]
    XCTAssertTrue(undo.waitForExistence(timeout: 4))
    undo.tap()
    XCTAssertEqual(native.value as? String, draft)
    app.buttons["Tools"].tap()
    app.buttons["Clipboard"].tap()
    let savedWord = app.buttons["Insert saved item, word"]
    XCTAssertTrue(savedWord.exists, app.debugDescription)
    savedWord.tap()
    XCTAssertEqual(native.value as? String, "wordword.")
    app.buttons["Undo · paste"].tap()
    XCTAssertEqual(native.value as? String, draft)
    app.buttons["Tools"].tap()
    app.buttons["Clipboard"].tap()
    let pin = app.buttons["Pin item"].firstMatch
    XCTAssertTrue(pin.exists, app.debugDescription)
    pin.tap()
    XCTAssertTrue(app.buttons["Unpin item"].firstMatch.exists)
    shot("advanced-clipboard", app)
    app.buttons["Delete item"].firstMatch.tap()
    XCTAssertFalse(app.buttons["Unpin item"].exists)
    app.launch()
    app.tabBars.buttons["Settings"].tap()
    setPreference(remember, enabled: false, in: app)
  }

  func testAdvancedSafariSmartDeleteProtectsNeighbors() throws {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: true)
    let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
    safari.open(URL(string: "http://127.0.0.1:4100/SafariFixture.html")!)
    let select = safari.buttons["Select misspelling"]
    try XCTSkipUnless(select.waitForExistence(timeout: 6), "Start the local Safari fixture server.")
    select.tap()
    selectExtension(in: safari)
    safari.buttons["Tools"].tap()
    safari.buttons["Clipboard"].tap()
    XCTAssertTrue(safari.buttons["Cut"].isEnabled)
    safari.buttons["Cut"].tap()
    let input = safari.textViews["Synthetic message"]
    let results = ["Draft one.\nPlease check  message.", "Draft one.\nPlease check message."]
    let cut = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
      results.contains(input.value as? String ?? "")
    }, object: input)
    XCTAssertEqual(XCTWaiter().wait(for: [cut], timeout: 4), .completed)
    XCTAssertFalse(safari.buttons["Undo · cut"].waitForExistence(timeout: 1.2))
  }

  func testAdvancedMessagesDraft() throws {
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
    let messages = XCUIApplication(bundleIdentifier: "com.apple.MobileSMS")
    messages.launch()
    for step in ["Continue", "Get Started", "Not Now"] {
      let option = messages.buttons[step]
      if option.exists && option.isHittable { option.tap() }
    }
    let compose = messages.buttons.matching(NSPredicate(format:
      "label CONTAINS[c] 'compose' OR label CONTAINS[c] 'new message' OR identifier CONTAINS[c] 'compose'")).firstMatch
    try XCTSkipUnless(compose.waitForExistence(timeout: 4), "Messages compose is unavailable on this simulator.")
    compose.tap()
    let body = messages.textViews.firstMatch
    try XCTSkipUnless(body.waitForExistence(timeout: 5) && body.isHittable,
      "Messages does not expose a body in its unaddressed composer.")
    body.tap()
    selectExtension(in: messages)
    chooseLayout("Banglish", in: messages)
    typeOnKotha("ami", in: messages)
    XCTAssertEqual(body.value as? String, "ami")
    shot("advanced-messages-unsent", messages)
    for _ in 0..<3 { messages.buttons["Backspace"].tap() }
    if messages.buttons["Cancel"].exists { messages.buttons["Cancel"].tap() }
  }

  func testAdvancedTouchSegmentationAndVoiceHandoff() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    app.buttons["Tools"].tap()
    app.buttons["dashboard-settings"].tap()
    for (name, desired) in [("Predictive suggestions", "On"), ("Autocorrect", "On"), ("Auto-capitalization", "Off")] {
      let key = app.buttons[name]
      if key.value as? String != desired { key.tap() }
    }
    app.buttons["Return to keyboard"].tap()
    typeOnKotha("gopd", in: app)
    XCTAssertEqual(app.buttons["candidate-0"].label, "good")
    for _ in 0..<4 { app.buttons["Backspace"].tap() }
    typeOnKotha("go", in: app)
    let p = app.buttons["p"]
    p.coordinate(withNormalizedOffset: CGVector(dx: 0.23, dy: 0.92)).tap()
    app.buttons["d"].tap()
    XCTAssertEqual(input.value as? String, "gopd")
    XCTAssertEqual(app.buttons["candidate-0"].label, "gold")
    shot("advanced-touch-alternative", app)
    for _ in 0..<4 { app.buttons["Backspace"].tap() }
    typeOnKotha("howareyou ", in: app)
    XCTAssertEqual(input.value as? String, "how are you ")
    shot("advanced-segmentation", app)
    app.buttons["Backspace"].tap()
    XCTAssertEqual(input.value as? String, "howareyou ")
    app.buttons["Tools"].tap()
    app.buttons["Voice input"].tap()
    let handoff = app.buttons["Switch keyboard"]
    XCTAssertTrue(handoff.exists)
    shot("advanced-voice-handoff", app)
    handoff.tap()
    XCTAssertTrue(app.buttons["Fix Grammar"].waitForNonExistence(timeout: 4))
  }

  private func clearLearnedWords(in app: XCUIApplication) {
    let clear = app.buttons["Clear learned words"].firstMatch
    for _ in 0..<6 {
      if clear.isHittable, clear.frame.midY < app.frame.maxY - 108 { break }
      app.swipeUp()
    }
    XCTAssertTrue(clear.isHittable, app.debugDescription)
    clear.tap()
    let confirm = app.sheets.buttons["Clear learned words"].firstMatch
    XCTAssertTrue(confirm.waitForExistence(timeout: 4), app.debugDescription)
    confirm.tap()
  }

  private func setPreference(_ control: XCUIElement, enabled: Bool, in app: XCUIApplication) {
    for _ in 0..<9 {
      if control.exists, control.isHittable, control.frame.midY < app.frame.maxY - 108 { break }
      app.swipeUp()
    }
    if (control.value as? String == "1") != enabled {
      control.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap()
    }
    XCTAssertEqual(control.value as? String, enabled ? "1" : "0")
  }

  func testPhaseElevenConsentAndDirectAI() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: true)
    app.tabBars.buttons["Settings"].tap()
    let reset = app.buttons["Reset AI shortcut consent"]
    for _ in 0..<3 where !reset.exists && !app.buttons["Reset anonymous session"].isHittable {
      app.swipeUp()
    }
    if reset.exists { tapAfterScrolling(reset, in: app) }
    tapAfterScrolling(app.buttons["Reset anonymous session"], in: app)
    XCTAssertTrue(app.staticTexts["Session reset."].waitForExistence(timeout: 8))

    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    typeOnKotha("can you send teh files.", in: app)
    let draft = input.value as? String
    app.buttons["Fix Grammar"].tap()
    let consent = app.buttons["Allow & Generate"]
    let generating = app.staticTexts["Generating…"]
    XCTAssertTrue(consent.exists)
    XCTAssertFalse(generating.exists)
    XCTAssertTrue(app.staticTexts.containing(NSPredicate(
      format: "label BEGINSWITH %@", "AI sends the draft below")).firstMatch.exists)
    shot("phase11-ai-consent", app)
    consent.tap()
    if generating.waitForExistence(timeout: 2) {
      shot("phase11-grammar-loading", app)
      app.buttons["Cancel"].tap()
      XCTAssertEqual(input.value as? String, draft)
      app.buttons["Fix Grammar"].tap()
      XCTAssertFalse(consent.exists)
    }
    let insert = app.buttons["Insert"]
    XCTAssertTrue(insert.waitForExistence(timeout: 22), app.debugDescription)
    XCTAssertEqual(input.value as? String, draft)
    shot("phase11-grammar-preview", app)
    app.buttons["Return to keyboard"].tap()

    app.buttons["Improve & Tone"].tap()
    app.buttons["Professional"].firstMatch.tap()
    app.buttons["Polite"].firstMatch.tap()
    XCTAssertTrue(insert.waitForExistence(timeout: 22), app.debugDescription)
    XCTAssertEqual(input.value as? String, draft)
    let obsolete = app.staticTexts.containing(NSPredicate(
      format: "label BEGINSWITH %@", "Could you please")).firstMatch
    XCTAssertFalse(obsolete.exists)
    shot("phase11-tone-preview", app)
    app.buttons["Return to keyboard"].tap()
    XCTAssertFalse(insert.exists)
    XCTAssertEqual(input.value as? String, draft)
  }

  func testPhaseElevenToneUsesEditedDraftAfterFailure() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: true)
    let input = showExtension(in: app)
    chooseLayout("English", in: app)
    typeOnKotha("mock failure seed.", in: app)
    XCTAssertEqual(input.value as? String, "mock failure seed.")

    app.buttons["Improve & Tone"].tap()
    app.buttons["Professional"].firstMatch.tap()
    startIfNeeded(in: app)
    let failed = app.staticTexts["The AI provider returned an unusable response."]
    XCTAssertTrue(failed.waitForExistence(timeout: 15), app.debugDescription)

    app.buttons["Edit input"].tap()
    typeOnKotha(" edited", in: app)
    app.buttons["Done"].tap()
    app.buttons["Polite"].firstMatch.tap()
    XCTAssertTrue(app.buttons["Insert"].waitForExistence(timeout: 20), app.debugDescription)
    let result = app.staticTexts.containing(NSPredicate(
      format: "label CONTAINS[c] %@", "seed. edited")).firstMatch
    XCTAssertTrue(result.exists, app.debugDescription)
    XCTAssertEqual(input.value as? String, "mock failure seed.")
    shot("phase11-tone-edited-retry", app)
  }

  private func generationButton(in app: XCUIApplication) -> XCUIElement {
    app.buttons.matching(
      NSPredicate(format: "label == %@ OR label == %@", "Generate", "Allow & Generate")
    ).firstMatch
  }

  private func startIfNeeded(in app: XCUIApplication) {
    let button = generationButton(in: app)
    if button.exists { button.tap() }
  }

  private func typeOnKotha(_ text: String, in app: XCUIApplication) {
    for c in text {
      let key = app.buttons[c == " " ? "Space" : String(c)].firstMatch
      if ".?!,;:".contains(c), !key.exists {
        app.buttons["123"].tap()
        key.tap()
        app.buttons["ABC"].tap()
      } else {
        key.tap()
      }
    }
  }

  private func openNumeric(in app: XCUIApplication) {
    app.buttons["Tools"].tap()
    app.buttons["Keyboard layout"].tap()
    app.buttons["Numbers"].tap()
  }

  private func configureAutocorrect(_ enabled: Bool, in app: XCUIApplication) {
    let control = app.switches["Autocorrect"]
    for _ in 0..<5 where !control.isHittable { app.swipeUp() }
    if (control.value as? String == "1") != enabled {
      control.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap()
    }
    XCTAssertEqual(control.value as? String, enabled ? "1" : "0")
  }

  private func configureKeyboard(in app: XCUIApplication, fullAccess desired: Bool) {
    let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
    let openSettings = app.buttons["Open Settings"]
    for _ in 0..<3 where !openSettings.isHittable { app.swipeUp() }
    openSettings.tap()
    XCTAssertTrue(settings.wait(for: .runningForeground, timeout: 10))
    let fullAccess = settings.switches["Allow Full Access"]
    let addKeyboard = settings.cells["AddNewKeyboard"]
    let addScreen = settings.navigationBars["Add New Keyboard"]
    let kotha = settings.cells.matching(
      NSPredicate(
        format: "label CONTAINS[c] %@", "Kotha"
      )
    ).firstMatch
    if !fullAccess.exists {
      if !addKeyboard.exists && !addScreen.exists {
        let general = settings.buttons["com.apple.settings.general"]
        let atRoot = general.exists
        if atRoot { general.tap() }
        let keyboard = settings.buttons["Keyboard"]
        if keyboard.exists || atRoot { tapAfterScrolling(keyboard, in: settings) }
        let keyboards = settings.buttons.matching(
          NSPredicate(
            format: "label MATCHES[c] %@", "Keyboards.*[0-9]"
          )
        ).firstMatch
        tapAfterScrolling(keyboards, in: settings)
      }
      if addScreen.exists {
        addScreen.buttons["Cancel"].tap()
      }
      if !kotha.exists {
        tapAfterScrolling(addKeyboard, in: settings)
        XCTAssertTrue(addScreen.waitForExistence(timeout: 5), settings.debugDescription)
        tapAfterScrolling(kotha, in: settings)
      }
      tapAfterScrolling(kotha, in: settings)
    }
    XCTAssertTrue(fullAccess.waitForExistence(timeout: 8), settings.debugDescription)
    if (fullAccess.value as? String == "1") != desired {
      fullAccess.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)).tap()
      if desired {
        let allow = settings.alerts.buttons["Allow"]
        if allow.waitForExistence(timeout: 4) { allow.tap() }
      }
    }
    XCTAssertEqual(fullAccess.value as? String, desired ? "1" : "0")
    // Toggling extension permissions can terminate its containing app while Settings is frontmost.
    app.launch()
  }

  private func showExtension(in app: XCUIApplication) -> XCUIElement {
    app.tabBars.buttons["Practice"].tap()
    let input = app.textViews["Your message"]
    input.tap()
    selectExtension(in: app)
    return input
  }

  private func selectExtension(in app: XCUIApplication) {
    let ai = app.buttons["Fix Grammar"]
    if !ai.waitForExistence(timeout: 3) {
      let globe = nextKeyboard(in: app)
      XCTAssertTrue(globe.exists, app.debugDescription)
      for _ in 0..<5 where !ai.exists {
        globe.tap()
        if ai.waitForExistence(timeout: 1.5) { break }
      }
    }
    XCTAssertTrue(ai.waitForExistence(timeout: 4), app.debugDescription)
  }

  private func chooseLayout(_ name: String, in app: XCUIApplication) {
    app.buttons["Tools"].tap()
    app.buttons["Typing language"].tap()
    app.buttons[name].firstMatch.tap()
  }

  private func tapAfterScrolling(_ element: XCUIElement, in app: XCUIApplication) {
    for _ in 0..<5 {
      if element.exists {
        let frame = element.frame
        let window = app.frame
        let point = CGPoint(x: frame.midX, y: frame.midY)
        if !frame.isEmpty, window.contains(point) {
          let coordinate = app.coordinate(withNormalizedOffset: .zero)
          coordinate.withOffset(CGVector(dx: point.x - window.minX, dy: point.y - window.minY))
            .tap()
          return
        }
      }
      app.swipeUp()
    }
    XCTFail("Can't locate a visible Settings row for \(element).\n\(app.debugDescription)")
  }

  private func nextKeyboard(in app: XCUIApplication) -> XCUIElement {
    app.descendants(matching: .any).matching(
      NSPredicate(
        format: "label CONTAINS[c] %@ OR label CONTAINS[c] %@",
        "Next keyboard", "Switch Keyboard"
      )
    ).firstMatch
  }
}
