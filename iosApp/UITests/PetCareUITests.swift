import XCTest

final class PetCareUITests: XCTestCase {
    func testLoginTwoSpacesAndLogout() {
        let app = XCUIApplication()
        app.launch()
        let fill = app.buttons["Заполнить демо-аккаунт"]
        XCTAssertTrue(fill.waitForExistence(timeout: 30))
        fill.tap()
        app.buttons["Войти"].tap()
        let family = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Семья")).firstMatch
        XCTAssertTrue(family.waitForExistence(timeout: 15))
        family.tap()
        XCTAssertTrue(app.staticTexts["Tom · кот"].waitForExistence(timeout: 15))
        app.buttons["Пространства"].tap()
        let dacha = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Дача")).firstMatch
        XCTAssertTrue(dacha.waitForExistence(timeout: 15))
        dacha.tap()
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
