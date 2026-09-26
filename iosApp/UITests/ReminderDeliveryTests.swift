import XCTest

/// Waits for real notifications, end to end: a check-in arriving on the home screen at the next
/// quarter hour, answered with "Got distracted" from the notification itself, and the follow-up the
/// planner derives from that answer ten minutes later. Everything the fast tests cannot reach —
/// the categories, the delegate, the Kotlin answer path — is on this one.
///
/// It takes up to half an hour of real time, so it runs only when asked:
///
///     TEST_RUNNER_DAYBLOCKS_DELIVERY_TESTS=1 xcodebuild test ... \
///       -only-testing:iosAppUITests/ReminderDeliveryTests
final class ReminderDeliveryTests: XCTestCase {

    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUpWithError() throws {
        try XCTSkipUnless(
            ProcessInfo.processInfo.environment["DAYBLOCKS_DELIVERY_TESTS"] != nil,
            "waits for real notifications; set TEST_RUNNER_DAYBLOCKS_DELIVERY_TESTS=1 to run it"
        )
        continueAfterFailure = false
    }

    func testACheckInIsAnsweredFromTheNotificationAndTheFollowUpComes() {
        let app = XCUIApplication()
        app.launchEnvironment["DAYBLOCKS_UITEST"] = "1"
        app.launchArguments += ["-app.onboarded", "YES"]
        app.launch()
        XCTAssertTrue(app.staticTexts["NOW"].waitForExistence(timeout: 20))
        allowNotificationsIfAsked(in: app)

        // New blocks start at the next quarter hour and last an hour. Two quarters earlier at both
        // ends centres the block on that quarter hour, so its check-in is the next thing due.
        let add = app.buttons.matching(NSPredicate(format: "label CONTAINS 'Add block'")).firstMatch
        XCTAssertTrue(add.waitForExistence(timeout: 10))
        add.tap()
        let title = app.textViews["What are you doing?"]
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        title.tap()
        title.typeText("Focus")
        let earlier = app.buttons.matching(identifier: "−")
        for _ in 0..<2 { earlier.element(boundBy: 0).tap() }
        for _ in 0..<2 { earlier.element(boundBy: 1).tap() }
        app.buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Today"].waitForExistence(timeout: 10))

        XCUIDevice.shared.press(.home)

        let checkIn = notification(containing: "Still on “Focus”")
        XCTAssertTrue(checkIn.waitForExistence(timeout: 16 * 60), "the check-in never arrived")
        checkIn.press(forDuration: 1.2) // expands the banner to show its buttons
        let distracted = springboard.buttons["Got distracted 😅"]
        XCTAssertTrue(distracted.waitForExistence(timeout: 10), "the check-in has no answer buttons")
        XCTAssertTrue(springboard.buttons["On it ✅"].exists)
        XCTAssertTrue(springboard.buttons["Skip this block"].exists)
        distracted.tap()

        let followUp = notification(containing: "No stress")
        XCTAssertTrue(followUp.waitForExistence(timeout: 11 * 60), "no follow-up after Got distracted")
    }

    /// A banner, or the notification it became, anywhere SpringBoard draws one.
    private func notification(containing text: String) -> XCUIElement {
        springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", text))
            .firstMatch
    }

    private func allowNotificationsIfAsked(in app: XCUIApplication) {
        let turnOn = app.buttons["Turn on"]
        guard turnOn.waitForExistence(timeout: 5) else { return }
        turnOn.tap()
        let allow = springboard.buttons["Allow"]
        XCTAssertTrue(allow.waitForExistence(timeout: 10), "iOS never asked")
        allow.tap()
    }
}
