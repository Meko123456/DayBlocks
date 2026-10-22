import XCTest

/// Picks a Home Screen quick action the way a person does: a long press on the app's icon, then
/// the action in the menu that opens. [type] is the action's UIApplicationShortcutItemType, which
/// SpringBoard uses as the menu row's identifier.
///
/// Slow by necessity: SpringBoard never reports itself idle while the menu is open, so the press
/// and the tap each wait out XCUITest's 60-second idle timeout. Apps without a widget can skip
/// that with one press that slides onto the first row (Nishani's tests do), but this menu starts
/// with the widget's sizes next to the icon, and letting go on one of those turns the icon into a
/// widget.
func useQuickAction(_ type: String, ofAppNamed name: String, file: StaticString = #filePath, line: UInt = #line) {
    let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    XCUIDevice.shared.press(.home)
    // Again, which takes the Home Screen back to its first page. An icon on another page has an
    // empty frame, which says nothing about which way it is, so the search starts there.
    XCUIDevice.shared.press(.home)
    XCTAssertTrue(springboard.icons[name].firstMatch.waitForExistence(timeout: 30), "\(name) is not on the Home Screen",
                  file: file, line: line)
    // The app's widget is named after the app as well, and is an icon to XCUITest too, at least
    // twice as wide as the app's own. One on another page has an empty frame.
    func appIcon() -> XCUIElement? {
        springboard.icons.matching(identifier: name).allElementsBoundByIndex
            .first { !$0.frame.isEmpty && $0.frame.width < 120 }
    }
    // A newly installed app goes on the last page.
    var pages = 0
    while appIcon()?.isHittable != true && pages < 5 {
        springboard.swipeLeft()
        pages += 1
    }
    guard let icon = appIcon() else {
        XCTFail("\(name)'s icon is on none of the first \(pages + 1) Home Screen pages", file: file, line: line)
        return
    }

    icon.press(forDuration: 1.0)
    let action = springboard.buttons[type]
    XCTAssertTrue(action.waitForExistence(timeout: 15), "the icon's menu has no \(type)", file: file, line: line)
    action.tap()
}
