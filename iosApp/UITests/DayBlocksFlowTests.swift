import XCTest

/// Drives the shared Compose UI on iOS the way a person would. The logic is already proven on this
/// simulator by the Kotlin tests; what only this can prove is that the screens, the navigation and
/// the database meet on iOS — that a block typed into the editor is saved and drawn on Today.
final class DayBlocksFlowTests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["DAYBLOCKS_UITEST"] = "1" // in-memory database: every run starts empty
        app.launch()
    }

    func testTodayOpensOnTheNowCard() {
        XCTAssertTrue(app.staticTexts["NOW"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["Free right now"].exists)
    }

    func testAddingABlockPutsItOnTheTimeline() {
        addBlock("Deep work")
        let saved = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'Deep work'")).firstMatch
        XCTAssertTrue(saved.waitForExistence(timeout: 10), "the saved block is not on Today")
    }

    func testSavingTodayAsATemplateListsItWithItsBlocks() {
        addBlock("Deep work")
        let templates = app.buttons["Templates"]
        XCTAssertTrue(templates.waitForExistence(timeout: 10))
        templates.tap()

        let saveToday = app.buttons["Save today as a template"]
        XCTAssertTrue(saveToday.waitForExistence(timeout: 10), "the Templates screen never opened")
        saveToday.tap()
        let name = app.textViews["Template name"]
        XCTAssertTrue(name.waitForExistence(timeout: 5), "the naming dialog never opened")
        name.tap()
        name.typeText("Weekday")
        app.buttons["Save"].tap()

        XCTAssertTrue(app.staticTexts["Weekday"].waitForExistence(timeout: 10), "the saved template is not listed")
        XCTAssertTrue(app.staticTexts["1 block"].exists, "the template does not carry today's block")
    }

    func testTurningOnRemindersAsksIOSAndClearsTheNotice() {
        XCTAssertTrue(app.staticTexts["NOW"].waitForExistence(timeout: 20))
        let notice = app.staticTexts.matching(NSPredicate(format: "label CONTAINS \"can't reach you\"")).firstMatch
        let turnOn = app.buttons["Turn on"]
        guard turnOn.waitForExistence(timeout: 5) else {
            // This simulator allowed notifications on an earlier run, and iOS only ever asks once.
            XCTAssertFalse(notice.exists, "notifications are allowed, yet the notice says otherwise")
            return
        }
        turnOn.tap()

        // The permission alert belongs to SpringBoard, not to the app.
        let allow = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["Allow"]
        XCTAssertTrue(allow.waitForExistence(timeout: 10), "iOS never asked")
        allow.tap()
        XCTAssertTrue(notice.waitForNonExistence(timeout: 10), "the notice is still up after allowing")
    }

    /// Adds a block through the editor with the default times and waits to be back on Today.
    private func addBlock(_ text: String) {
        // "matching", not "containing": the FAB's own label is "+  Add block", and containing looks
        // at descendants rather than at the element itself.
        let add = app.buttons.matching(NSPredicate(format: "label CONTAINS 'Add block'")).firstMatch
        XCTAssertTrue(add.waitForExistence(timeout: 20), "the Add block button never appeared")
        add.tap()

        XCTAssertTrue(app.staticTexts["New block"].waitForExistence(timeout: 10), "the editor never opened")
        // Compose's OutlinedTextField reaches the iOS accessibility tree as a text *view*, labelled
        // with its placeholder — not as a text field, which is what app.textFields looks for.
        let title = app.textViews["What are you doing?"]
        XCTAssertTrue(title.waitForExistence(timeout: 5))
        title.tap()
        title.typeText(text)

        let save = app.buttons["Save"]
        XCTAssertTrue(save.waitForExistence(timeout: 5))
        save.tap()

        // Back on Today, drawn from the database the editor just wrote to.
        XCTAssertTrue(app.staticTexts["Today"].waitForExistence(timeout: 10), "did not return to Today")
    }
}
