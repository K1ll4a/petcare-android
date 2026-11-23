package com.petcare.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petcare.app.presentation.account.AccountViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Реальные Compose-экраны на Android: общий вход, навигация и выход из защищённого раздела. */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Before fun logout() {
        ui.runOnIdle {
            val container = (ui.activity.application as PetCareApplication).container
            ViewModelProvider(ui.activity, AccountViewModel.Factory(container.accountRepository))[AccountViewModel::class.java].controller.logout()
        }
    }
    private fun waitText(text: String) {
        try {
            ui.waitUntil(15_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) { throw AssertionError("Ожидался экран: $text\n${ui.onRoot().printToString()}", e) }
    }
    private fun click(text: String) {
        waitText(text)
        val matcher = hasText(text) and hasClickAction() and isEnabled()
        ui.waitUntil(15_000) { ui.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        val node = ui.onNode(matcher)
        if (ui.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) node.performScrollTo()
        node.performClick()
    }
    private fun back() { ui.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } }

    @Test fun loginTwoSpacesProfileTaskAndLogout() {
        click("Заполнить демо-аккаунт"); click("Войти"); click("Семья")
        waitText("Tom · кот")
        click("Профиль"); waitText("Профиль питомца"); click("К задачам")
        click("+ Задача"); waitText("Новая задача"); back(); waitText("Tom · кот")
        click("Пространства"); click("Дача"); waitText("Мурка · кот")
        ui.onNodeWithText("Tom · кот").assertDoesNotExist()
        ui.onNodeWithText("Приглашения").assertDoesNotExist()
        // Участник «Дачи»: в настройках нет управления составом и приглашений, но есть выход (#28).
        click("Настройки"); waitText("Ваша роль: участник"); waitText("Олег")
        ui.onNodeWithText("Приглашения").assertDoesNotExist()
        ui.onNodeWithText("Удалить").assertDoesNotExist()
        ui.onNodeWithText("Передать владение").assertDoesNotExist()
        waitText("Выйти из пространства")
        click("К задачам"); waitText("Мурка · кот")
        click("Выйти"); waitText("Выйти из аккаунта?")
        ui.onAllNodesWithText("Выйти")[1].performClick()
        waitText("Добро пожаловать")
        ui.onNodeWithText("Мурка · кот").assertDoesNotExist()
        // Пересоздание Activity после выхода тоже не восстанавливает защищённый экран.
        ui.activityRule.scenario.recreate(); waitText("Добро пожаловать")
    }

    @Test fun registerCreateSpaceAndRevokeInvitation() {
        click("Создать аккаунт")
        ui.onNodeWithText("Имя").performTextInput("Тестовый участник")
        ui.onNodeWithText("Email").performTextInput("ui${System.nanoTime()}@example.com")
        ui.onNodeWithText("Пароль").performTextInput("password123")
        ui.onNodeWithText("Повторите пароль").performTextInput("password123")
        click("Зарегистрироваться"); click("Создать пространство")
        ui.onNodeWithText("Название").performTextInput("UI пространство")
        click("Создать"); click("UI пространство")
        waitText("В пространстве пока нет питомцев. Нажмите «+ Питомец».")
        click("Настройки"); waitText("Ваша роль: владелец")
        waitText("Вы единственный участник. Чтобы выйти, пригласите нового участника и передайте ему владение.")
        click("Приглашения"); click("Создать приглашение")
        click("Отозвать"); waitText("Отозвано")
        ui.onNodeWithText("Отозвать").assertDoesNotExist()
    }
}
