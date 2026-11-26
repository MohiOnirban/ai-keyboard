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
    app.buttons["Generate"].tap()
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
    app.buttons["Generate"].tap()
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
    XCTAssertGreaterThan(app.buttons["Space"].frame.width, q.width * 3)
    print("KOTHA_GEOMETRY q=\(q) a=\(a) z=\(z) space=\(app.buttons["Space"].frame)")
    shot("01-english-light", app)
    app.buttons["q"].press(forDuration: 0.7)
    XCTAssertEqual(input.value as? String, "1")
    app.buttons["?123"].tap()
    shot("02-symbols", app)
    app.buttons["=\\<"].tap()
    shot("03-secondary-symbols", app)
    app.buttons["123"].tap()
    shot("04-numeric", app)
    XCTAssertEqual(app.buttons["0"].frame.midX, app.buttons["2"].frame.midX, accuracy: 1)
    app.buttons["0"].tap()
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
    // A punctuation key returns from local completions to the toolbar.
    app.buttons["."].tap()
    let draft = input.value as? String
    let top = app.buttons["Fix Grammar"].frame.minY
    let bottom = app.buttons["Space"].frame.maxY
    for (i, label) in ["Fix Grammar", "Improve & Tone", "Ask AI", "Translation"].enumerated() {
      app.buttons[label].tap()
      let back = app.buttons["Return to keyboard"]
      XCTAssertTrue(back.exists)
      XCTAssertFalse(app.buttons["q"].exists)
      XCTAssertEqual(back.frame.minY, top, accuracy: 1)
      XCTAssertEqual(app.buttons["Generate"].frame.maxY, bottom, accuracy: 1)
      shot("ai-\(i)-input", app)
      if label == "Translation" {
        app.buttons["Edit input"].tap()
        for _ in (draft ?? "") { app.buttons["Backspace"].tap() }
        for ch in "How are you" {
          if ch == "H" { app.buttons["Shift"].tap() }
          app.buttons[ch == " " ? "Space" : String(ch)].firstMatch.tap()
        }
        app.buttons["?123"].tap()
        app.buttons["?"].tap()
        app.buttons["Done"].tap()
      }
      app.buttons["Generate"].tap()
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
    app.buttons["Emoji"].tap()
    shot("utility-emoji", app)
    app.buttons["Return to keyboard"].tap()
    app.buttons["Clipboard"].tap()
    shot("utility-clipboard", app)
    app.buttons["Return to keyboard"].tap()
    app.buttons["More options"].tap()
    shot("utility-more", app)
    app.buttons["Return to keyboard"].tap()
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
    XCTAssertTrue(
      app.staticTexts.containing(NSPredicate(format: "label BEGINSWITH %@", "Selected passage"))
        .firstMatch.exists, app.debugDescription)
    app.buttons["Generate"].tap()
    if app.staticTexts["Generating…"].waitForExistence(timeout: 2) {
      app.buttons["Cancel"].tap()
      XCTAssertEqual(input.value as? String, "teh.")
      app.buttons["Fix Grammar"].tap()
      app.buttons["Generate"].tap()
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
    app.buttons["?123"].tap()
    app.buttons["123"].tap()
    XCTAssertEqual(app.buttons["0"].frame.midX, app.buttons["2"].frame.midX, accuracy: 1)
    shot("04-numeric", app)
    app.buttons["?123"].tap()
    XCTAssertTrue(app.buttons["@"].exists)
    app.buttons["ABC"].tap()
    captureLandscape(in: app)
    for c in "i like you." { app.buttons[c == " " ? "Space" : String(c)].firstMatch.tap() }
    let draft = input.value as? String
    app.buttons["Improve & Tone"].tap()
    app.buttons["Generate"].tap()
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
      for _ in 0..<5 where !ai.exists { globe.tap() }
    }
    XCTAssertTrue(ai.waitForExistence(timeout: 4), app.debugDescription)
  }

  private func chooseLayout(_ name: String, in app: XCUIApplication) {
    let choices = app.buttons["Typing language"]
    if choices.exists { choices.tap() } else { app.buttons["Space"].press(forDuration: 0.8) }
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
