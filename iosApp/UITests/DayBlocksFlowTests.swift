import XCTest

/// Drives the shared Compose UI on iOS the way a person would. The logic is already proven on this
/// simulator by the Kotlin tests; what only this can prove is that the screens, the navigation and
/// the database meet on iOS — that a block typed into the editor is saved and drawn on Today.
final class DayBlocksFlowTests: XCTestCase {

    private var app: XCUIApplication!

    // Waits are generous on purpose. They return the moment the element appears, so they cost
    // nothing when the app is fast, and a CI simulator can take a minute over one test.
    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["DAYBLOCKS_UITEST"] = "1" // in-memory database: every run starts empty
        // A launch argument is a user default for this run only: these tests start on Today, and
        // OnboardingTests is the one that starts where a new user does.
        app.launchArguments += ["-app.onboarded", "YES"]
        app.launch()
    }

    func testTodayOpensOnTheNowCard() {
        XCTAssertTrue(app.staticTexts["NOW"].waitForExistence(timeout: 30))
        XCTAssertTrue(app.staticTexts["Free right now"].exists)
    }

    func testAddingABlockPutsItOnTheTimeline() {
        addBlock("Deep work")
        let saved = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'Deep work'")).firstMatch
        XCTAssertTrue(saved.waitForExistence(timeout: 30), "the saved block is not on Today")
    }

    func testSavingTodayAsATemplateListsItWithItsBlocks() {
        addBlock("Deep work")
        let templates = app.buttons["Templates"]
        XCTAssertTrue(templates.waitForExistence(timeout: 30))
        templates.tapWhenHittable()

        let saveToday = app.buttons["Save today as a template"]
        XCTAssertTrue(saveToday.waitForExistence(timeout: 30), "the Templates screen never opened")
        saveToday.tapWhenHittable()
        let name = app.textViews["Template name"]
        XCTAssertTrue(name.waitForExistence(timeout: 30), "the naming dialog never opened")
        name.typeAfterFocusing("Weekday")
        app.buttons["Save"].tapWhenHittable()

        XCTAssertTrue(app.staticTexts["Weekday"].waitForExistence(timeout: 30), "the saved template is not listed")
        XCTAssertTrue(app.staticTexts["1 block"].exists, "the template does not carry today's block")
    }

    /// Delete asks first, says what goes, and keeps the template when the answer is no. It used to
    /// take the template at one tap.
    func testDeletingATemplateAsksFirst() {
        addBlock("Deep work")
        let templates = app.buttons["Templates"]
        XCTAssertTrue(templates.waitForExistence(timeout: 30))
        templates.tapWhenHittable()
        let saveToday = app.buttons["Save today as a template"]
        XCTAssertTrue(saveToday.waitForExistence(timeout: 30), "the Templates screen never opened")
        saveToday.tapWhenHittable()
        let name = app.textViews["Template name"]
        XCTAssertTrue(name.waitForExistence(timeout: 30), "the naming dialog never opened")
        name.typeAfterFocusing("Weekday\n")
        let listed = app.staticTexts["1 block"]
        XCTAssertTrue(listed.waitForExistence(timeout: 30), "the saved template is not listed")

        app.buttons["Delete"].tapWhenHittable()
        let question = app.staticTexts["Delete “Weekday”?"]
        XCTAssertTrue(question.waitForExistence(timeout: 30), "Delete took the template without asking")
        XCTAssertTrue(app.staticTexts["Its 1 block goes with it."].exists, "the question did not say what goes")
        app.buttons["Keep it"].tapWhenHittable()
        XCTAssertTrue(question.waitForNonExistence(timeout: 30), "Keep it did not close the question")
        XCTAssertTrue(listed.exists, "Keep it deleted the template anyway")

        app.buttons["Delete"].tapWhenHittable()
        XCTAssertTrue(question.waitForExistence(timeout: 30), "the second Delete did not ask either")
        // Two buttons say Delete while the question is up: the card's, and the dialog's on top of it.
        app.buttons.matching(identifier: "Delete").allElementsBoundByIndex.last { $0.isHittable }?.tap()
        XCTAssertTrue(listed.waitForNonExistence(timeout: 30), "the confirmed delete left the template listed")
    }

    func testTheBuddyGreetsOnTodayAndAnswersToANewName() {
        let kubi = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Kubi,'")).firstMatch
        XCTAssertTrue(kubi.waitForExistence(timeout: 30), "the buddy is not on Today")
        kubi.tapWhenHittable()

        let name = app.textViews["Name"]
        XCTAssertTrue(name.waitForExistence(timeout: 30), "tapping the buddy did not offer a rename")
        name.typeAfterFocusing(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 10) + "Bloop")
        app.buttons["Save"].tapWhenHittable()

        let bloop = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Bloop,'")).firstMatch
        XCTAssertTrue(bloop.waitForExistence(timeout: 30), "the buddy did not take its new name")
    }

    /// The keyboard's return key does what the dialog's Save does; it used to only close the keyboard.
    func testTheReturnKeySavesATemplate() {
        addBlock("Deep work")
        let templates = app.buttons["Templates"]
        XCTAssertTrue(templates.waitForExistence(timeout: 30))
        templates.tapWhenHittable()

        let saveToday = app.buttons["Save today as a template"]
        XCTAssertTrue(saveToday.waitForExistence(timeout: 30), "the Templates screen never opened")
        saveToday.tapWhenHittable()
        let name = app.textViews["Template name"]
        XCTAssertTrue(name.waitForExistence(timeout: 30), "the naming dialog never opened")
        name.typeAfterFocusing("Weekday\n")

        XCTAssertTrue(app.staticTexts["Weekday"].waitForExistence(timeout: 30), "return did not save the template")
    }

    func testTheReturnKeyRenamesTheBuddy() {
        let kubi = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Kubi,'")).firstMatch
        XCTAssertTrue(kubi.waitForExistence(timeout: 30), "the buddy is not on Today")
        kubi.tapWhenHittable()

        let name = app.textViews["Name"]
        XCTAssertTrue(name.waitForExistence(timeout: 30), "tapping the buddy did not offer a rename")
        name.typeAfterFocusing(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 10) + "Bloop\n")

        let bloop = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Bloop,'")).firstMatch
        XCTAssertTrue(bloop.waitForExistence(timeout: 30), "return did not rename the buddy")
    }

    func testANameCutAtTheLimitKeepsWholeCharacters() {
        let kubi = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Kubi,'")).firstMatch
        XCTAssertTrue(kubi.waitForExistence(timeout: 30), "the buddy is not on Today")
        kubi.tapWhenHittable()

        let name = app.textViews["Name"]
        XCTAssertTrue(name.waitForExistence(timeout: 30), "tapping the buddy did not offer a rename")
        // Nineteen letters and 🔥, which is two UTF-16 units: one over the 20 a name may have.
        // take(20) kept the first half of the emoji, and the buddy was named with a broken character.
        let letters = String(repeating: "x", count: 19)
        name.typeAfterFocusing(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 10) + letters + "🔥")
        app.buttons["Save"].tapWhenHittable()

        let renamed = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", letters + ",")).firstMatch
        XCTAssertTrue(renamed.waitForExistence(timeout: 30), "the name was not cut back to the 19 letters")
    }

    func testRatingABlockAtTheCheckInScoresTheDay() {
        addBlock("Deep work")
        app.buttons["Check-in"].tapWhenHittable()
        XCTAssertTrue(app.staticTexts["How did today go?"].waitForExistence(timeout: 30), "the check-in never opened")
        XCTAssertTrue(app.staticTexts["Nothing rated yet"].exists)

        let done = app.descendants(matching: .any).matching(NSPredicate(format: "label == 'Done'")).firstMatch
        XCTAssertTrue(done.waitForExistence(timeout: 30))
        done.tapWhenHittable()
        XCTAssertTrue(app.staticTexts["100%"].waitForExistence(timeout: 30), "rating the only block did not score the day")
    }

    func testASettingChangedReachesTodayAtOnce() {
        let settings = app.buttons["Settings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 30))
        settings.tapWhenHittable()
        XCTAssertTrue(app.staticTexts["Your buddy"].waitForExistence(timeout: 30), "Settings never opened")
        let name = app.textViews["Name"]
        XCTAssertTrue(name.waitForExistence(timeout: 30))
        name.typeAfterFocusing(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 10) + "Bloop")
        app.buttons["‹ Back"].tapWhenHittable()

        let bloop = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Bloop,'")).firstMatch
        XCTAssertTrue(bloop.waitForExistence(timeout: 30), "Today still shows the old name")
    }

    func testTurningOnRemindersAsksIOSAndClearsTheNotice() {
        XCTAssertTrue(app.staticTexts["NOW"].waitForExistence(timeout: 30))
        let notice = app.staticTexts.matching(NSPredicate(format: "label CONTAINS \"can't reach you\"")).firstMatch
        let turnOn = app.buttons["Turn on"]
        // A probe for something that may rightly never appear, so not the generous wait: on a
        // simulator that allowed notifications before, this is the path that has to be quick.
        guard turnOn.waitForExistence(timeout: 10) else {
            // This simulator allowed notifications on an earlier run, and iOS only ever asks once.
            XCTAssertFalse(notice.exists, "notifications are allowed, yet the notice says otherwise")
            return
        }
        turnOn.tapWhenHittable()

        // The permission alert belongs to SpringBoard, not to the app.
        let allow = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["Allow"]
        XCTAssertTrue(allow.waitForExistence(timeout: 30), "iOS never asked")
        allow.tapWhenHittable()
        XCTAssertTrue(notice.waitForNonExistence(timeout: 30), "the notice is still up after allowing")
    }

    /// The Home Screen's Add a block: Today again from wherever the app was left, and its editor,
    /// as + opens it, so the block lands on the day being planned.
    func testAddABlockFromTheHomeScreen() {
        let templates = app.buttons["Templates"]
        XCTAssertTrue(templates.waitForExistence(timeout: 30))
        templates.tapWhenHittable()
        XCTAssertTrue(app.buttons["Save today as a template"].waitForExistence(timeout: 30), "the Templates screen never opened")

        useQuickAction("io.github.meko123456.dayblocks.new-block", ofAppNamed: "DayBlocks")

        XCTAssertTrue(app.staticTexts["New block"].waitForExistence(timeout: 30), "the quick action did not open the block editor")
        // Saving goes back to Today, not to Templates: the editor was opened over Today.
        saveBlock("From the Home Screen")
        let saved = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'From the Home Screen'")).firstMatch
        XCTAssertTrue(saved.waitForExistence(timeout: 30), "the block is not on Today")
    }

    /// The widget's link, dayblocks://today, brings Today back from another screen. The quick
    /// action's scene delegate sits in the way a link comes in, so it is checked alongside it.
    func testTheWidgetLinkOpensToday() {
        let templates = app.buttons["Templates"]
        XCTAssertTrue(templates.waitForExistence(timeout: 30))
        templates.tapWhenHittable()
        XCTAssertTrue(app.buttons["Save today as a template"].waitForExistence(timeout: 30), "the Templates screen never opened")

        XCUIDevice.shared.system.open(URL(string: "dayblocks://today")!)

        XCTAssertTrue(app.staticTexts["NOW"].waitForExistence(timeout: 30), "the widget's link did not open Today")
    }

    /// Adds a block through the editor with the default times and waits to be back on Today.
    private func addBlock(_ text: String) {
        // "matching", not "containing": the FAB's own label is "+  Add block", and containing looks
        // at descendants rather than at the element itself.
        let add = app.buttons.matching(NSPredicate(format: "label CONTAINS 'Add block'")).firstMatch
        XCTAssertTrue(add.waitForExistence(timeout: 30), "the Add block button never appeared")
        // Tapped once it is hittable, and again if the editor has not come up. On a busy CI runner
        // (the run for ae6aa0e, 17 October 2026) the one tap went nowhere and "the editor never
        // opened". Once the editor is up the button is gone, so the second tap cannot open two.
        let hittable = XCTNSPredicateExpectation(predicate: NSPredicate(format: "isHittable == true"), object: add)
        _ = XCTWaiter().wait(for: [hittable], timeout: 30)
        add.tap()
        let editor = app.staticTexts["New block"]
        if !editor.waitForExistence(timeout: 15), add.exists, add.isHittable { add.tap() }

        XCTAssertTrue(editor.waitForExistence(timeout: 30), "the editor never opened")
        saveBlock(text)
    }

    /// Types [text] into the open block editor, saves with the default times, and waits to be back
    /// on Today.
    private func saveBlock(_ text: String) {
        // Compose's OutlinedTextField reaches the iOS accessibility tree as a text *view*, labelled
        // with its placeholder — not as a text field, which is what app.textFields looks for.
        let title = app.textViews["What are you doing?"]
        XCTAssertTrue(title.waitForExistence(timeout: 30))
        title.typeAfterFocusing(text)

        let save = app.buttons["Save"]
        XCTAssertTrue(save.waitForExistence(timeout: 30))
        // Tapped once it is enabled, and again if the editor is still up. On a busy CI runner (14
        // October 2026) the tap came 1.7 s after the typing and changed nothing: either the title
        // had not reached the editor, so Save was still disabled, or the tap only took the field's
        // focus away. A second tap can't save twice, because a save closes the editor.
        let enabled = XCTNSPredicateExpectation(predicate: NSPredicate(format: "isEnabled == true"), object: save)
        _ = XCTWaiter().wait(for: [enabled], timeout: 30)
        save.tap()
        if !app.staticTexts["Today"].waitForExistence(timeout: 15), save.exists { save.tap() }

        // Back on Today, drawn from the database the editor just wrote to.
        XCTAssertTrue(app.staticTexts["Today"].waitForExistence(timeout: 30), "did not return to Today")
    }
}
