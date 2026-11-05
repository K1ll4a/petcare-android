package com.petcare.app

import com.petcare.app.data.fake.DemoAccountRepository
import com.petcare.app.domain.model.SpaceRole
import com.petcare.app.domain.repository.ApiException
import com.petcare.app.domain.repository.DemoStateStore
import com.petcare.app.platform.passwordHash
import com.petcare.app.platform.secureRandomBytes
import com.petcare.app.presentation.edit.TaskEditController
import com.petcare.app.presentation.home.SpaceTimeFormatter
import com.petcare.app.time.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlin.test.*
import kotlin.time.Duration.Companion.hours

/** These same contracts run on Android/JVM and inside the iOS simulator. */
class CrossPlatformContractTest {
    @Test fun passwordHashMatchesExistingAndroidAccounts() {
        assertEquals("9ea4c8987ef46376958c315135738776140bf89370fe26e416db8ef0259b07ac", passwordHash("password123", "00112233445566778899aabbccddeeff"))
    }
    @Test fun unicodePasswordHasSameHashOnBothPlatforms() {
        assertEquals("3dbe848e7f28aa7c2b98174542ebdfe2d7ac0a244c881ff54608b9f3b6f601ac", passwordHash("пароль123🐾", "00112233445566778899aabbccddeeff"))
    }
    @Test fun randomInvitationsUsePlatformSecureRandom() {
        assertEquals(32, secureRandomBytes(32).size)
    }
    @Test fun timezoneAndRussianDateFormattingArePortable() {
        val zone = TimeZone.of("Europe/Moscow")
        val formatter = SpaceTimeFormatter(zone, LocalDate(2026, 11, 5))
        assertEquals("сегодня, 01:30", formatter.format(Instant.parse("2026-11-04T22:30:00Z")))
        assertEquals("7 ноября, 15:00", formatter.format(Instant.parse("2026-11-07T12:00:00Z")))
        assertEquals("Europe/Moscow, UTC+03:00", formatter.zoneLabel(Instant.parse("2026-11-05T12:00:00Z")))
    }
    @Test fun daylightSavingUsesOffsetAtTaskTime() {
        val zone = TimeZone.of("Europe/Berlin")
        assertEquals("+02:00", zone.offsetLabel(Instant.parse("2026-07-01T10:00:00Z")))
        assertEquals("+01:00", zone.offsetLabel(Instant.parse("2026-12-01T10:00:00Z")))
    }
    @Test fun dateAndTimeValidationRemainStrict() {
        assertNull(TaskEditController.parseDate("31.02.2026"))
        assertNull(TaskEditController.parseDate("5.11.2026"))
        assertNull(TaskEditController.parseTime("24:01"))
        assertEquals(LocalDate(2028, 2, 29), TaskEditController.parseDate("29.02.2028"))
        assertEquals(LocalTime(20, 30), TaskEditController.parseTime("20:30"))
    }
    @Test fun registrationInvitationsAndRolesWorkOnBothPlatforms() = runTest {
        val store = object : DemoStateStore {
            var value: String? = null
            override fun read() = value
            override fun write(value: String) { this.value = value }
        }
        val now = Instant.parse("2026-11-05T10:00:00Z")
        val clock = object : Clock { override fun now() = now }
        val accounts = DemoAccountRepository(store, clock, delayMs = 0)
        accounts.login("grisha@example.com", "demo12345")
        assertEquals(listOf(SpaceRole.OWNER, SpaceRole.MEMBER), accounts.getSpaces().map { it.role })
        val invite = accounts.createInvitation(1)
        assertEquals(now + 24.hours, invite.expiresAt)
        accounts.logout()
        assertNull(accounts.session.value)
        accounts.register("Участник", "cross@example.com", "password123")
        assertTrue(accounts.getSpaces().isEmpty())
        assertEquals(SpaceRole.MEMBER, accounts.joinSpace(invite.code).role)
        assertFailsWith<ApiException> { accounts.joinSpace(invite.code) }
        assertFailsWith<ApiException> { accounts.createInvitation(1) }
        accounts.logout()
        val restored = DemoAccountRepository(store, clock, delayMs = 0)
        assertNull(restored.session.value)
        restored.login("cross@example.com", "password123")
        assertEquals(1L, restored.getSpaces().single().id)
    }
}
