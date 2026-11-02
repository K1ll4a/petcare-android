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
        ui.waitUntil(15_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(text: String) {
        waitText(text)
        ui.waitUntil(15_000) { ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val node = ui.onNodeWithText(text)
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
        click("Приглашения"); click("Создать приглашение")
        click("Отозвать"); waitText("Отозвано")
        ui.onNodeWithText("Отозвать").assertDoesNotExist()
    }
}
