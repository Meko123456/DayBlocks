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
