package com.petcare.app

import com.petcare.app.data.fake.DemoAccountRepository
import com.petcare.app.data.fake.DemoFixture
import com.petcare.app.data.fake.FakePetCareRepository
import com.petcare.app.data.fake.RawJsonSource
import com.petcare.app.data.fake.SessionPetCareRepository
import com.petcare.app.domain.model.*
import com.petcare.app.domain.repository.ApiException
import com.petcare.app.domain.repository.TelegramRepository
import com.petcare.app.navigation.TaskLink
import com.petcare.app.presentation.account.AccountController
import com.petcare.app.presentation.home.HomeController
import com.petcare.app.presentation.home.HomeUiState
import com.petcare.app.presentation.settings.TelegramController
import kotlinx.coroutines.*
import kotlinx.datetime.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/** Задача #28: состав группы, Telegram, ссылка на задачу и отзыв доступа. */
class GroupTelegramAccessTest {
    private val store = MemoryDemoStore()
    private var now = Instant.parse("2026-11-23T10:00:00Z")
    private val clock = object : Clock { override fun now() = this@GroupTelegramAccessTest.now }
    private fun accounts() = DemoAccountRepository(store, clock, delayMs = 0)
    private suspend fun expectError(code: Int, block: suspend () -> Unit): ApiException {
        try { block(); fail("Expected $code") } catch (e: ApiException) { assertEquals(code, e.httpCode); return e }
        throw AssertionError()
    }

    // Демо-данные: «Семья» (1) — владелец Гриша (3), участник Аня (4); «Дача» (2) — владелец Олег (5), участник Гриша.

    // ---------------- состав группы ----------------

    @Test fun members_visibleToAnyMember_ownerFirst() = runBlocking {
        val r = accounts(); r.login("anna@example.com", "demo12345")
        assertEquals(listOf("Гриша" to SpaceRole.OWNER, "Аня" to SpaceRole.MEMBER), r.getMembers(1).map { it.name to it.role })
        expectError(403) { r.getMembers(2) }
    }

    @Test fun memberCannotManageGroup() = runBlocking {
        val r = accounts(); r.login("anna@example.com", "demo12345")
        assertEquals("not_owner", expectError(403) { r.removeMember(1, 3) }.code)
        assertEquals("not_owner", expectError(403) { r.transferOwnership(1, 4) }.code)
    }

    @Test fun ownerCannotLeave_untilTransfer_thenLeaves_andOneOwnerRemains() = runBlocking {
        val r = accounts(); r.login("grisha@example.com", "demo12345")
        assertTrue(expectError(409) { r.leaveSpace(1) }.message.contains("передайте владение"))
        r.transferOwnership(1, 4)
        val roles = r.getMembers(1).associate { it.id to it.role }
        assertEquals(1, roles.values.count { it == SpaceRole.OWNER })
        assertEquals(SpaceRole.OWNER, roles[4]); assertEquals(SpaceRole.MEMBER, roles[3])
        r.leaveSpace(1)
        assertEquals(listOf(2L), r.getSpaces().map { it.id }) // «Дача» осталась
        // Новое владение сохраняется после перезапуска.
        val restored = accounts(); restored.login("anna@example.com", "demo12345")
        assertEquals(SpaceRole.OWNER, restored.getSpaces().first { it.id == 1L }.role)
    }

    @Test fun soleOwner_getsExplanation() = runBlocking {
        val r = accounts(); r.login("anna@example.com", "demo12345")
        val space = r.createSpace("Своё", "Europe/Moscow")
        assertTrue(expectError(409) { r.leaveSpace(space.id) }.message.contains("единственный участник"))
    }

    @Test fun removedMember_losesThatSpaceOnly() = runBlocking {
        val r = accounts(); r.login("oleg@example.com", "demo12345")
        expectError(400) { r.removeMember(2, 5) } // себя — нельзя
        r.removeMember(2, 3)
        r.logout(); r.login("grisha@example.com", "demo12345")
        assertEquals(listOf(1L), r.getSpaces().map { it.id })
        expectError(403) { r.requireSpace(2) }
        r.requireSpace(1) // доступ к другому пространству остаётся
    }

    @Test fun removedMembersOpenTasks_goToOwner_doneStayInHistory() = runBlocking {
        val r = accounts()
        val data = FakePetCareRepository(RawJsonSource { DemoFixture.json }, clock = clock, baseDelayMs = 0)
        r.onMemberGone = { spaceId, userId, ownerId, ownerName -> data.reassignOpenTasks(spaceId, userId, ownerId, ownerName) }
        val session = SessionPetCareRepository(r, data)
        r.login("grisha@example.com", "demo12345")
        val before = (session.getPets(1).flatMap { session.getTaskItems(1, it.id) })
        val annaOpen = before.filter { it.task.assigneeId == 4L && it.status in setOf(TaskStatus.UPCOMING, TaskStatus.OVERDUE) }
        val annaDone = before.filter { it.completedById == 4L }
        r.removeMember(1, 4)
        val after = session.getPets(1).flatMap { session.getTaskItems(1, it.id) }
        annaOpen.forEach { t -> assertEquals(3L, after.first { it.key == t.key }.task.assigneeId) }
        annaDone.forEach { t -> assertEquals(4L, after.first { it.key == t.key }.completedById) }
    }

    // ---------------- Telegram ----------------

    @Test fun telegram_notConfirmedUntilBotStart_andStateSurvivesRestart() = runBlocking {
        val r = accounts(); r.login("grisha@example.com", "demo12345")
        assertFalse(r.getStatus().isConfirmed)
        val link = r.createLink()
        assertTrue(link.url.startsWith("https://t.me/"))
        assertEquals(now + 10.minutes, link.expiresAt)
        assertFalse(r.getStatus().isConfirmed) // ссылка выдана, бот не запущен
        expectError(409) { r.setNotifications(false) }
        r.simulateBotStart()
        assertEquals(TelegramStatus(isConfirmed = true, notificationsEnabled = true), r.getStatus())
        r.setNotifications(false)
        val restored = accounts(); restored.login("grisha@example.com", "demo12345")
        assertEquals(TelegramStatus(isConfirmed = true, notificationsEnabled = false), restored.getStatus())
        restored.logout(); restored.login("anna@example.com", "demo12345")
        assertFalse(restored.getStatus().isConfirmed) // привязка — у конкретного аккаунта
    }

    @Test fun telegram_expiredLink_doesNotConfirm_andUnlinkClears() = runBlocking {
        val r = accounts(); r.login("grisha@example.com", "demo12345")
        r.createLink(); now += 11.minutes
        expectError(409) { r.simulateBotStart() }
        assertFalse(r.getStatus().isConfirmed)
        r.createLink(); r.simulateBotStart(); r.unlink()
        assertFalse(r.getStatus().isConfirmed)
    }

    @Test fun telegramController_switchFollowsServer_andErrorKeepsValue() = runBlocking {
        val r = accounts(); r.login("grisha@example.com", "demo12345")
        r.createLink(); r.simulateBotStart()
        var fail = false
        val repo = object : TelegramRepository by r {
            override suspend fun setNotifications(enabled: Boolean): TelegramStatus {
                if (fail) throw kotlinx.io.IOException("нет сети")
                return r.setNotifications(enabled)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val c = TelegramController(scope, repo, r, clock)
            c.load(); waitFor { !c.state.value.loading }
            assertTrue(c.state.value.status!!.notificationsEnabled)
            fail = true
            c.setNotifications(false); waitFor { !c.state.value.busy }
            // Не подтверждено — переключатель остаётся включённым, ошибка видна.
            assertTrue(c.state.value.status!!.notificationsEnabled)
            assertTrue(c.state.value.error!!.contains("соединения"))
            fail = false
            c.setNotifications(false); waitFor { !c.state.value.busy }
            assertFalse(c.state.value.status!!.notificationsEnabled)
        } finally { scope.cancel() }
    }

    @Test fun telegramController_waitsForBot_untilServerConfirms() = runBlocking {
        val r = accounts(); r.login("grisha@example.com", "demo12345")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val c = TelegramController(scope, r, r, clock)
            c.load(); waitFor { !c.state.value.loading }
            c.createLink(); waitFor { !c.state.value.busy }
            assertTrue(c.state.value.waitingForBot)
            c.pollConfirmation(); delay(20)
            assertTrue(c.state.value.waitingForBot) // бот ещё не запущен
            r.simulateBotStart()
            c.pollConfirmation(); waitFor { !c.state.value.waitingForBot }
            assertTrue(c.state.value.status!!.isConfirmed)
        } finally { scope.cancel() }
    }

    // ---------------- ссылка на задачу и отзыв доступа ----------------

    private fun setup(): Triple<DemoAccountRepository, SessionPetCareRepository, AccountController> {
        val r = accounts()
        val data = FakePetCareRepository(RawJsonSource { DemoFixture.json }, clock = clock, baseDelayMs = 0)
        val session = SessionPetCareRepository(r, data)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return Triple(r, session, AccountController(scope, r, session))
    }

    @Test fun taskLink_opensOwnTask_inItsSpace() = runBlocking {
        val (r, session, c) = setup()
        c.login("grisha@example.com", "demo12345"); awaitIdle(c)
        val task = session.getTaskItems(2, session.getPets(2).first().id).first()
        c.openTaskLink(TaskLink(task.task.id, task.occurrenceId)); awaitIdle(c)
        assertEquals(2L, c.state.value.selectedSpaceId)
        assertEquals(task.task.id, c.state.value.linkedTask!!.task.id)
        c.consumeLinkedTask(); assertNull(c.state.value.linkedTask)
        r.logout()
    }

    @Test fun taskLink_otherAccount_seesNoTaskData() = runBlocking {
        val (r, session, c) = setup()
        r.login("grisha@example.com", "demo12345")
        val dachaTask = session.getTaskItems(2, session.getPets(2).first().id).first()
        r.logout()
        c.login("anna@example.com", "demo12345"); awaitIdle(c) // Аня не состоит в «Даче»
        c.openTaskLink(TaskLink(dachaTask.task.id, dachaTask.occurrenceId)); awaitIdle(c)
        assertNull(c.state.value.linkedTask)
        assertNull(c.state.value.selectedSpaceId)
        val error = c.state.value.error!!
        assertTrue(error.contains("недоступна"))
        assertFalse(error.contains(dachaTask.task.title)) // название задачи не раскрывается
    }

    @Test fun accessRevoked_closesOnlyThatSpace() = runBlocking {
        val (_, _, c) = setup()
        c.login("grisha@example.com", "demo12345"); awaitIdle(c)
        c.selectSpace(2)
        c.onAccessRevoked(2); awaitIdle(c)
        assertNull(c.state.value.selectedSpaceId)
        assertTrue(c.state.value.notice!!.contains("Доступ к пространству «Дача» закрыт"))
        assertTrue(c.state.value.spaces.any { it.id == 1L })
    }

    @Test fun homeController_reportsAccessLoss_insteadOfSwitchingItself() = runBlocking {
        val repo = ControlledRepo()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val lost = mutableListOf<Long>()
        try {
            val home = HomeController(scope, repo, repo, repo, fixedClock(now, TimeZone.UTC), onAccessLost = { lost += it })
            home.start(1); yield()
            repo.fail("pets:1", ApiException(403, "forbidden", "Нет доступа")); yield()
            assertEquals(listOf(1L), lost)
            assertEquals(HomeUiState.Loading, home.state.value) // данные закрытого пространства убраны сразу
        } finally { scope.cancel() }
    }

    private suspend fun awaitIdle(c: AccountController) = withTimeout(5000) { while (c.state.value.busy) delay(5) }
    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(5000) { while (!condition()) delay(5) }
}
