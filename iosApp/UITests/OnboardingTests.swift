import XCTest

/// Starts where a new user does: fresh settings, nothing planned, and no launch argument saying
/// onboarding is done. Walks the whole introduction and lands on Today with a first template.
final class OnboardingTests: XCTestCase {

    func testANewUserMeetsTheBuddyAndStartsFromTheExampleDay() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchEnvironment["DAYBLOCKS_UITEST"] = "1"
        app.launch()

        XCTAssertTrue(app.staticTexts["Hi! I'm Kubi."].waitForExistence(timeout: 30), "onboarding did not open")
        app.buttons["Nice to meet you"].tap()

        XCTAssertTrue(app.staticTexts["How should I talk to you?"].waitForExistence(timeout: 30))
        // Each tone speaks for itself before it is chosen.
        let sample = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'Deep work'")).firstMatch
        XCTAssertTrue(sample.exists, "the tones have no sample lines")
        app.buttons["That's the one"].tap()

        // The explanation comes first; iOS is only asked if the user says yes.
        XCTAssertTrue(app.staticTexts["Can I tap you on the shoulder?"].waitForExistence(timeout: 30))
        app.buttons["Not now"].tap()

        XCTAssertTrue(app.staticTexts["Let's plan today."].waitForExistence(timeout: 30))
        app.buttons["Start from an example day"].tap()

        XCTAssertTrue(app.staticTexts["Today"].waitForExistence(timeout: 30), "onboarding did not end on Today")
        app.buttons["Templates"].tap()
        XCTAssertTrue(app.staticTexts["Example day"].waitForExistence(timeout: 30), "the example day was not kept as a template")
    }
}
