package com.petcare.app

import com.petcare.app.data.remote.TelegramWire
import com.petcare.app.data.remote.dto.JsonCodec
import com.petcare.app.navigation.PendingDeepLinks
import com.petcare.app.navigation.TaskLink
import com.petcare.app.navigation.TaskLinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Ссылка из Telegram и формат Telegram-методов сервера (API.md 1.2 §7.1). Без дат и платформ. */
class DeepLinkAndTelegramWireTest {

    // ---------- petcare://tasks/{task_id}?occurrence_id={id} ----------

    @Test
    fun parsesOnceAndDailyLinks() {
        assertEquals(TaskLink(8, null), TaskLinkParser.parse("petcare://tasks/8"))
        assertEquals(TaskLink(1, 5), TaskLinkParser.parse("petcare://tasks/1?occurrence_id=5"))
        assertEquals(TaskLink(1, 5), TaskLinkParser.parse("  PETCARE://tasks/1/?occurrence_id=5&utm=tg#x "))
    }

    @Test
    fun emptyOrNoneOccurrence_isOnceTask() {
        // Мост сервера подставляет occurrence_id из шаблона; у разовой задачи он пустой.
        assertEquals(TaskLink(8, null), TaskLinkParser.parse("petcare://tasks/8?occurrence_id="))
        assertEquals(TaskLink(8, null), TaskLinkParser.parse("petcare://tasks/8?occurrence_id=None"))
    }

    @Test
    fun foreignOrBrokenLinks_areIgnored() {
        listOf(
            "https://t.me/petcare_demo_bot",
            "petcare://pets/8",
            "petcare://tasks/",
            "petcare://tasks/abc",
            "petcare://tasks/0",
            "petcare://tasks/-3",
            "petcare://tasks/8/history",
            "petcare://tasks/8?occurrence_id=x",
            "",
        ).forEach { assertNull(it, TaskLinkParser.parse(it)) }
    }

    @Test
    fun pendingLink_waitsAndIsTakenOnce() {
        PendingDeepLinks.take()
        assertFalse(PendingDeepLinks.offer("https://example.com"))
        assertFalse(PendingDeepLinks.offer(null))
        assertNull(PendingDeepLinks.link.value)
        assertTrue(PendingDeepLinks.offer("petcare://tasks/3"))
        assertTrue(PendingDeepLinks.offer("petcare://tasks/4?occurrence_id=9")) // новая заменяет прежнюю
        assertEquals(TaskLink(4, 9), PendingDeepLinks.link.value)
        assertEquals(TaskLink(4, 9), PendingDeepLinks.take())
        assertNull(PendingDeepLinks.take())
    }

    // ---------- Telegram ----------

    @Test
    fun status_confirmedAndNotifications() {
        val s = TelegramWire.status("""{ "is_confirmed": true, "notifications_enabled": false }""")
        assertTrue(s.isConfirmed)
        assertFalse(s.notificationsEnabled)
    }

    @Test
    fun status_missingFields_meansNotConfirmed() {
        val s = TelegramWire.status("{}")
        assertFalse(s.isConfirmed)
        assertFalse(s.notificationsEnabled)
    }

    @Test
    fun link_parsesContractExample() {
        val l = TelegramWire.link(
            """{"link_url": "https://t.me/petcare_demo_bot?start=AbC", "bot_username": "petcare_demo_bot", "expires_at": "2026-11-12T12:10:00Z"}""",
        )
        assertEquals("https://t.me/petcare_demo_bot?start=AbC", l.linkUrl)
        assertEquals("petcare_demo_bot", l.botUsername)
        assertEquals("2026-11-12T12:10:00Z", l.expiresAt)
    }

    @Test
    fun link_notToTelegram_isRejected() {
        try {
            TelegramWire.link("""{"link_url": "https://evil.example/x"}""")
            fail()
        } catch (e: JsonCodec.FormatException) {
            assertTrue(e.message!!.contains("Telegram"))
        }
    }

    @Test
    fun notificationsBody() {
        assertEquals("""{"notifications_enabled":false}""", TelegramWire.notificationsBody(false))
    }
}
