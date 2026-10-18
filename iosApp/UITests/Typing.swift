import XCTest

extension XCUIElement {
    /// Taps the field, and types once it has the keyboard.
    ///
    /// On a busy CI runner the tap can land before the field takes focus, and `typeText` then fails
    /// with "Neither element nor any descendant has keyboard focus" — the run for 8b6c011 on
    /// 17 October 2026 lost testTheReturnKeySavesATemplate that way, at the block title. A second
    /// tap on a field that already has the keyboard changes nothing, so it is safe to retry once.
    func typeAfterFocusing(_ text: String) {
        tap()
        let focused = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hasKeyboardFocus == true"), object: self)
        if XCTWaiter().wait(for: [focused], timeout: 10) != .completed { tap() }
        typeText(text)
    }
}

extension XCUIElement {
    /// Taps once the element can actually be hit, failing the test if it never can.
    ///
    /// An element can be in the tree before it can take a tap: the app still coming to the front, a
    /// dialog still animating in. A tap then is synthesised where nothing listens, and the test
    /// fails later, somewhere else, for the wrong reason. The run for ae6aa0e (17 October 2026)
    /// lost its Add block tap that way.
    func tapWhenHittable(timeout: TimeInterval = 30, file: StaticString = #filePath, line: UInt = #line) {
        let hittable = XCTNSPredicateExpectation(predicate: NSPredicate(format: "isHittable == true"), object: self)
        XCTAssertEqual(XCTWaiter().wait(for: [hittable], timeout: timeout), .completed,
                       "\(self) never became hittable", file: file, line: line)
        tap()
    }
}

