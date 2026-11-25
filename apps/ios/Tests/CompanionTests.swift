import XCTest

final class CompanionTests: XCTestCase {
  func testOnboardingPracticeAndMockGeneration() throws {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    XCTAssertTrue(app.staticTexts["Kotha Keys"].waitForExistence(timeout: 10))
    app.buttons["Try the practice area"].tap()
    let input = app.textViews["Your message"]
    XCTAssertTrue(input.waitForExistence(timeout: 5))
    input.tap()
    if app.buttons["AI"].waitForExistence(timeout: 2) {
      nextKeyboard(in: app).tap()
    }
    input.typeText("Meeting postponed today")
    app.buttons["Generate suggestion"].tap()
    XCTAssertTrue(app.buttons["Use suggestion"].waitForExistence(timeout: 25), app.debugDescription)
    app.buttons["Use suggestion"].tap()
    XCTAssertNotEqual(input.value as? String, "Meeting postponed today")
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
    XCTAssertTrue(app.staticTexts.containing(NSPredicate(
      format: "label BEGINSWITH %@ AND label ENDSWITH %@", "0 of ", " generations used today."
    )).firstMatch.waitForExistence(timeout: 8))
  }

  func testSystemKeyboardOfflineTyping() {
    continueAfterFailure = false
    let app = XCUIApplication()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
    let input = showExtension(in: app)
    let ai = app.buttons["AI"]
    ai.tap()
    XCTAssertTrue(app.staticTexts["Allow Full Access to use AI."].exists)
    app.buttons["Close"].tap()
    let layout = ["English", "বাংলা", "Phonetic", "Banglish"]
      .map { app.buttons[$0].firstMatch }.first { $0.exists }
    XCTAssertNotNil(layout, app.debugDescription)
    layout?.tap()
    app.buttons["Phonetic"].tap()
    app.buttons["⇧"].tap()
    app.buttons["A"].tap()
    app.buttons["x"].tap()
    app.buttons["⌫"].tap()
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
    let layout = ["English", "বাংলা", "Phonetic", "Banglish"]
      .map { app.buttons[$0].firstMatch }.first { $0.exists }
    XCTAssertNotNil(layout, app.debugDescription)
    if layout?.label != "English" {
      layout?.tap()
      app.buttons["English"].tap()
    }
    for letter in "meeting" { app.buttons[String(letter)].firstMatch.tap() }
    app.buttons["Space"].tap()
    app.buttons["AI"].tap()
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
    try XCTSkipUnless(title.waitForExistence(timeout: 10), "Start the local Safari fixture server described in the iOS README.")

    XCUIDevice.shared.orientation = .landscapeLeft
    defer { XCUIDevice.shared.orientation = .portrait }
    XCTAssertTrue(safari.frame.width > safari.frame.height)
    let field = safari.textViews["Synthetic message"]
    let input = field.exists ? field : safari.textFields["Synthetic message"]
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
    app.buttons["AI"].tap()
    app.buttons["Generate"].tap()
    let insert = app.buttons["Insert"]
    XCTAssertTrue(insert.waitForExistence(timeout: 20), app.debugDescription)
    app.buttons["x"].tap()
    XCTAssertEqual(input.value as? String, "meeting x")
    insert.tap()
    XCTAssertEqual(input.value as? String, "meeting x")
    app.buttons["Cancel"].tap()
    XCTAssertFalse(insert.exists)
    XCTAssertTrue(app.buttons["AI"].exists)
    XCTAssertEqual(input.value as? String, "meeting x")

    app.terminate()
    app.launch()
    configureKeyboard(in: app, fullAccess: false)
  }

  private func configureKeyboard(in app: XCUIApplication, fullAccess desired: Bool) {
    let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
    app.buttons["Open Settings"].tap()
    XCTAssertTrue(settings.wait(for: .runningForeground, timeout: 10))
    let fullAccess = settings.switches["Allow Full Access"]
    let addKeyboard = settings.cells["AddNewKeyboard"]
    let addScreen = settings.navigationBars["Add New Keyboard"]
    let kotha = settings.cells.matching(NSPredicate(
      format: "label CONTAINS[c] %@", "Kotha"
    )).firstMatch
    if !fullAccess.exists {
      if !addKeyboard.exists && !addScreen.exists {
        let general = settings.buttons["com.apple.settings.general"]
        let atRoot = general.exists
        if atRoot { general.tap() }
        let keyboard = settings.buttons["Keyboard"]
        if keyboard.exists || atRoot { tapAfterScrolling(keyboard, in: settings) }
        let keyboards = settings.buttons.matching(NSPredicate(
          format: "label MATCHES[c] %@", "Keyboards.*[0-9]"
        )).firstMatch
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
    app.activate()
  }

  private func showExtension(in app: XCUIApplication) -> XCUIElement {
    app.buttons["Try the practice area"].tap()
    let input = app.textViews["Your message"]
    input.tap()
    selectExtension(in: app)
    return input
  }

  private func selectExtension(in app: XCUIApplication) {
    let ai = app.buttons["AI"]
    if !ai.waitForExistence(timeout: 3) {
      let globe = nextKeyboard(in: app)
      XCTAssertTrue(globe.exists, app.debugDescription)
      for _ in 0..<5 where !ai.exists { globe.tap() }
    }
    XCTAssertTrue(ai.waitForExistence(timeout: 4), app.debugDescription)
  }

  private func chooseLayout(_ name: String, in app: XCUIApplication) {
    let layout = ["English", "বাংলা", "Phonetic", "Banglish"]
      .map { app.buttons[$0].firstMatch }.first { $0.exists }
    XCTAssertNotNil(layout, app.debugDescription)
    if layout?.label != name {
      layout?.tap()
      app.buttons[name].tap()
    }
  }

  private func tapAfterScrolling(_ element: XCUIElement, in app: XCUIApplication) {
    for _ in 0..<5 {
      if element.exists {
        let frame = element.frame
        let window = app.frame
        let point = CGPoint(x: frame.midX, y: frame.midY)
        if !frame.isEmpty, window.contains(point) {
          let coordinate = app.coordinate(withNormalizedOffset: .zero)
          coordinate.withOffset(CGVector(dx: point.x - window.minX, dy: point.y - window.minY)).tap()
          return
        }
      }
      app.swipeUp()
    }
    XCTFail("Can't locate a visible Settings row for \(element).\n\(app.debugDescription)")
  }

  private func nextKeyboard(in app: XCUIApplication) -> XCUIElement {
    app.descendants(matching: .any).matching(NSPredicate(
      format: "label CONTAINS[c] %@ OR label CONTAINS[c] %@",
      "Next keyboard", "Switch Keyboard"
    )).firstMatch
  }
}
