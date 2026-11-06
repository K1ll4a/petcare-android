import XCTest

final class PetCareUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func tapWhenReady(_ element: XCUIElement, timeout: TimeInterval = 15) {
        XCTAssertTrue(element.waitForExistence(timeout: timeout))
        let enabled = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [enabled], timeout: timeout), .completed)
        element.tap()
    }

    func testLoginTwoSpacesAndLogout() {
        let app = XCUIApplication()
        app.launch()
        let fill = app.buttons["Заполнить демо-аккаунт"]
        XCTAssertTrue(fill.waitForExistence(timeout: 30))
        tapWhenReady(fill)
        tapWhenReady(app.buttons["Войти"])
        let family = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Семья")).firstMatch
        XCTAssertTrue(family.waitForExistence(timeout: 15))
        tapWhenReady(family)
        XCTAssertTrue(app.staticTexts["Tom · кот"].waitForExistence(timeout: 15))
        tapWhenReady(app.buttons["Пространства"])
        let dacha = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Дача")).firstMatch
        XCTAssertTrue(dacha.waitForExistence(timeout: 15))
        tapWhenReady(dacha)
        XCTAssertTrue(app.staticTexts["Мурка · кот"].waitForExistence(timeout: 15))
        XCTAssertFalse(app.staticTexts["Tom · кот"].exists)
        XCTAssertFalse(app.buttons["Приглашения"].exists)
        app.buttons["Выйти"].tap()
        XCTAssertTrue(app.staticTexts["Выйти из аккаунта?"].waitForExistence(timeout: 5))
        app.buttons.matching(NSPredicate(format: "label == %@", "Выйти")).allElementsBoundByIndex.last!.tap()
        XCTAssertTrue(app.buttons["Заполнить демо-аккаунт"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Мурка · кот"].exists)
    }
}
