package com.petcare.app

import kotlinx.datetime.*
import com.petcare.app.time.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import com.petcare.app.data.fake.*
import com.petcare.app.domain.model.*
import com.petcare.app.domain.repository.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

internal class MemoryDemoStore : DemoStateStore {
    var value: String? = null
    override fun read() = value
    override fun write(value: String) { this.value = value }
}

class AccountRepositoryTest {
    private val store = MemoryDemoStore()
    private val start = Instant.parse("2026-11-02T10:00:00Z")
    private var now = start
    private val clock = object : Clock { override fun now() = this@AccountRepositoryTest.now }
    private fun repo(delayMs: Long = 0) = DemoAccountRepository(store, clock, delayMs)
    private suspend fun expectError(code: Int, block: suspend () -> Unit) {
        try { block(); fail("Expected $code") } catch (e: ApiException) { assertEquals(code, e.httpCode) }
    }

    @Test fun demoLogin_hasTwoSpacesAndSeparateRoles() = runBlocking {
        val r = repo(); r.login(" GRISHA@example.com ", "demo12345")
        assertEquals(listOf("Семья", "Дача"), r.getSpaces().map { it.name })
        assertEquals(listOf(SpaceRole.OWNER, SpaceRole.MEMBER), r.getSpaces().map { it.role })
    }
    @Test fun wrongPassword_doesNotCreateSession() = runBlocking {
        val r = repo(); expectError(401) { r.login("grisha@example.com", "wrong") }; assertNull(r.session.value)
    }
    @Test fun register_createsEmptyAccount_andLoginWorksAfterRestart() = runBlocking {
        val r = repo(); r.register("Нина", "Nina@example.com", "password123")
        assertTrue(r.getSpaces().isEmpty()); r.logout()
        val restored = repo(); assertNull(restored.session.value)
        restored.login("nina@example.com", "password123"); assertEquals("Нина", restored.session.value!!.name)
        assertFalse(store.value!!.contains("password123"))
    }
    @Test fun invalidRegistration_andDuplicateEmail_areRejected() = runBlocking {
        val r = repo()
        expectError(422) { r.register("", "n@example.com", "password123") }
        expectError(422) { r.register("Нина", "invalid", "password123") }
        expectError(422) { r.register("Нина", "n@example.com", "short") }
        expectError(409) { r.register("Гриша", "GRISHA@example.com", "password123") }
        assertNull(r.session.value)
    }
    @Test fun createSpace_validatesNameZone_andRestoresOwner() = runBlocking {
        val r = repo(); r.login("anna@example.com", "demo12345")
        expectError(422) { r.createSpace(" ", "Europe/Moscow") }
        expectError(422) { r.createSpace("Дом", "Mars/Olympus") }
        val space = r.createSpace(" Новый дом ", "Asia/Tokyo")
        assertEquals("Новый дом", space.name); assertEquals(SpaceRole.OWNER, space.role)
        val restored = repo(); restored.login("anna@example.com", "demo12345")
        assertEquals(SpaceRole.OWNER, restored.getSpaces().first { it.id == space.id }.role)
    }
    @Test fun invite_isOneUse_andMembershipIsSharedAndPersistent() = runBlocking {
        val r = repo(); r.login("grisha@example.com", "demo12345")
        val invitation = r.createInvitation(1)
        assertEquals((start + 86400.seconds), invitation.expiresAt)
        r.logout(); r.register("Нина", "nina@example.com", "password123")
        val space = r.joinSpace(invitation.code.lowercase())
        assertEquals(SpaceRole.MEMBER, space.role); assertEquals(1, r.getSpaces().size)
        expectError(409) { r.joinSpace(invitation.code) }
        r.logout(); r.login("grisha@example.com", "demo12345")
        assertTrue(r.getInvitations(1).single().used)
        assertTrue(r.members(1).any { it.name == "Нина" })
        val restored = repo(); restored.login("nina@example.com", "password123")
        assertEquals(1L, restored.getSpaces().single().id)
    }
    @Test fun expiredAtBoundary_andRevokedInvite_doNotGrantAccess() = runBlocking {
        val r = repo(); r.login("grisha@example.com", "demo12345")
        val expired = r.createInvitation(1); val revoked = r.createInvitation(1)
        r.revokeInvitation(1, revoked.code)
        r.logout(); r.register("Нина", "nina@example.com", "password123")
        expectError(409) { r.joinSpace(revoked.code) }
        now = (start + 86400.seconds)
        expectError(409) { r.joinSpace(expired.code) }
        assertTrue(r.getSpaces().isEmpty())
    }
    @Test fun alreadyMember_doesNotConsumeCode_orDuplicateMembership() = runBlocking {
        val r = repo(); r.login("grisha@example.com", "demo12345"); val invite = r.createInvitation(1)
        expectError(409) { r.joinSpace(invite.code) }
        assertFalse(r.getInvitations(1).single().used); assertEquals(2, r.members(1).size)
    }
    @Test fun memberCannotManageInvitations_andStrangerCannotReadSpace() = runBlocking {
        val r = repo(); r.login("grisha@example.com", "demo12345")
        expectError(403) { r.createInvitation(2) }; expectError(403) { r.getInvitations(2) }
        expectError(403) { r.revokeInvitation(2, "random") }
        r.logout(); r.register("Нина", "nina@example.com", "password123")
        expectError(403) { r.requireSpace(1) }; expectError(422) { r.joinSpace("bad-code") }
    }
    @Test fun logoutRevokesLocalAccess_andDoesNotRestoreSession() = runBlocking {
        val r = repo(); r.login("grisha@example.com", "demo12345"); val old = r.session.value
        r.logout(); assertNull(r.session.value)
        expectError(401) { r.getSpaces() }; expectError(401) { r.createSpace("Дом", "UTC") }
        expectError(401) { r.requireSpace(1) }; assertNull(repo().session.value)
        r.login("grisha@example.com", "demo12345"); assertNotEquals(old!!.generation, r.session.value!!.generation)
    }
    @Test fun logoutDuringPendingLogin_doesNotRestoreAccess() = runBlocking {
        val r = repo(30)
        val pending = async(start = CoroutineStart.UNDISPATCHED) { r.login("grisha@example.com", "demo12345") }
        r.logout(); pending.await(); assertNull(r.session.value)
    }
    @Test fun sessionRepositories_rejectProtectedReadsAfterLogout() = runBlocking {
        val accounts = repo(); accounts.login("grisha@example.com", "demo12345")
        val data = FakePetCareRepository(RawJsonSource { DemoFixture.json }, baseDelayMs = 0)
        val guarded = SessionPetCareRepository(accounts, data)
        assertEquals("Tom", guarded.getPets(1).first().name)
        accounts.logout()
        expectError(401) { guarded.getPets(1) }; expectError(401) { guarded.getPetTypes() }
    }
    @Test fun newSpace_isEmpty_andSupportsNewPetsAndTasks() = runBlocking {
        val accounts = repo(); accounts.register("Нина", "nina@example.com", "password123")
        val space = accounts.createSpace("Дом", "Europe/Moscow")
        val data = FakePetCareRepository(RawJsonSource { DemoFixture.json }, baseDelayMs = 0)
        val guarded = SessionPetCareRepository(accounts, data)
        assertTrue(guarded.getPets(space.id).isEmpty())
        val pet = guarded.createPet(space.id, PetDraft(name = "Барсик", typeId = 1))
        assertEquals(listOf(pet.id), guarded.getPets(space.id).map { it.id })
        val draft = TaskDraft(title = "Покормить", petId = pet.id, assigneeId = accounts.session.value!!.userId, date = LocalDate.parse("2026-11-03"), time = LocalTime(12, 0))
        guarded.createTask(space, draft)
        assertEquals("Покормить", guarded.getTaskItems(space.id, pet.id).single().task.title)
        expectError(403) { guarded.getPets(1) }
    }
    @Test fun lateReadFromPreviousAccount_isRejected() = runBlocking {
        val accounts = repo(); accounts.login("grisha@example.com", "demo12345")
        val data = FakePetCareRepository(RawJsonSource { DemoFixture.json }, baseDelayMs = 30)
        val guarded = SessionPetCareRepository(accounts, data)
        val result = async(start = CoroutineStart.UNDISPATCHED) { runCatching { guarded.getPets(1) } }
        accounts.logout(); accounts.login("anna@example.com", "demo12345")
        assertTrue(result.await().exceptionOrNull() is ApiException)
    }
}
