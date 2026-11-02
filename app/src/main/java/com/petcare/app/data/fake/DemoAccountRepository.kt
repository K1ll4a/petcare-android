package com.petcare.app.data.fake

import com.petcare.app.data.remote.dto.JsonCodec
import com.petcare.app.domain.model.*
import com.petcare.app.domain.repository.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Учебный сервер на одном устройстве: аккаунты, членство и одноразовые приглашения.
 * Метаданные сохраняются, сессия после перезапуска требует нового входа.
 * Это не защита серверного API и не способ обмена данными между устройствами.
 */
class DemoAccountRepository(
    private val store: DemoStateStore,
    private val clock: Clock = Clock.systemUTC(),
    private val delayMs: Long = 250,
) : AccountRepository {
    private data class User(val id: Long, val name: String, val email: String, val salt: String, val hash: String)
    private val users = mutableListOf<User>()
    private val spaces = mutableListOf<Space>()
    private val memberships = mutableMapOf<Long, MutableMap<Long, SpaceRole>>()
    private val invitations = mutableListOf<Invitation>()
    @Volatile private var generation = 0L
    private val mutableSession = MutableStateFlow<AccountSession?>(null)
    override val session = mutableSession.asStateFlow()

    init {
        val saved = store.read()
        if (saved == null) seed() else restore(saved)
    }

    override suspend fun login(email: String, password: String) {
        val expected = generation
        delay(delayMs)
        val user = users.firstOrNull { it.email == normalize(email) }
        if (user == null || hash(password, user.salt) != user.hash) fail(401, "Неверный email или пароль.")
        if (expected != generation) return
        mutableSession.value = AccountSession(user.id, user.name, user.email, ++generation)
    }

    override suspend fun register(name: String, email: String, password: String) {
        val expected = generation
        delay(delayMs)
        val cleanEmail = normalize(email)
        if (name.isBlank()) fail(422, "Введите имя.")
        if (!Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""").matches(cleanEmail)) fail(422, "Введите корректный email.")
        if (password.length < 8) fail(422, "Пароль должен содержать хотя бы 8 символов.")
        if (users.any { it.email == cleanEmail }) fail(409, "Аккаунт с таким email уже существует.")
        if (expected != generation) return
        val user = newUser((users.maxOfOrNull { it.id } ?: 0) + 1, name.trim(), cleanEmail, password)
        users += user
        persist()
        mutableSession.value = AccountSession(user.id, user.name, user.email, ++generation)
    }

    override fun logout() {
        ++generation
        mutableSession.value = null
    }

    override suspend fun getSpaces(): List<Space> {
        val user = awaitSession()
        return spaces.mapNotNull { s -> memberships[s.id]?.get(user.userId)?.let { s.copy(role = it) } }
    }

    override suspend fun createSpace(name: String, timezone: String): Space {
        val user = awaitSession()
        if (name.isBlank()) fail(422, "Введите название пространства.")
        val zone = runCatching { ZoneId.of(timezone.trim()) }.getOrElse { fail(422, "Укажите часовой пояс, например Europe/Moscow.") }
        val space = Space(maxOf(100L, (spaces.maxOfOrNull { it.id } ?: 0) + 1), name.trim(), zone.id, zone, SpaceRole.OWNER)
        spaces += space
        memberships[space.id] = mutableMapOf(user.userId to SpaceRole.OWNER)
        persist()
        return space
    }

    override suspend fun joinSpace(code: String): Space {
        val user = awaitSession()
        val invite = invitations.firstOrNull { it.code == code.trim().uppercase(Locale.ROOT) }
            ?: fail(422, "Приглашение не найдено. Проверьте код.")
        if (!invite.isActive(clock.instant())) fail(409, "Код истёк, отозван или уже использован. Попросите новый.")
        val members = memberships.getValue(invite.spaceId)
        if (members.containsKey(user.userId)) fail(409, "Вы уже участвуете в этом пространстве.")
        members[user.userId] = SpaceRole.MEMBER
        invitations[invitations.indexOf(invite)] = invite.copy(used = true)
        persist()
        return spaces.first { it.id == invite.spaceId }.copy(role = SpaceRole.MEMBER)
    }

    override suspend fun getInvitations(spaceId: Long): List<Invitation> {
        requireOwner(spaceId, awaitSession())
        return invitations.filter { it.spaceId == spaceId }
    }

    override suspend fun createInvitation(spaceId: Long): Invitation {
        requireOwner(spaceId, awaitSession())
        var code: String
        do { code = randomHex(6).uppercase(Locale.ROOT) } while (invitations.any { it.code == code })
        return Invitation(code, spaceId, clock.instant().plus(Duration.ofHours(24))).also { invitations += it; persist() }
    }

    override suspend fun revokeInvitation(spaceId: Long, code: String) {
        requireOwner(spaceId, awaitSession())
        val index = invitations.indexOfFirst { it.spaceId == spaceId && it.code == code }
        if (index < 0) fail(404, "Приглашение не найдено.")
        if (!invitations[index].isActive(clock.instant())) fail(409, "Приглашение уже недействительно.")
        invitations[index] = invitations[index].copy(revoked = true)
        persist()
    }

    /** Проверка членства перед каждым обращением к учебным питомцам/задачам. */
    fun requireSpace(spaceId: Long): Space {
        val user = session.value ?: fail(401, "Войдите в аккаунт.")
        val role = memberships[spaceId]?.get(user.userId) ?: fail(403, "Нет доступа к пространству.")
        return spaces.first { it.id == spaceId }.copy(role = role)
    }

    fun members(spaceId: Long): List<Member> {
        requireSpace(spaceId)
        return memberships.getValue(spaceId).map { (id, role) -> Member(id, users.first { it.id == id }.name, role) }
    }

    private suspend fun awaitSession(): AccountSession {
        val before = session.value ?: fail(401, "Войдите в аккаунт.")
        delay(delayMs)
        if (before != session.value) fail(401, "Сессия завершена. Войдите снова.")
        return before
    }
    private fun requireOwner(spaceId: Long, user: AccountSession) {
        if (memberships[spaceId]?.get(user.userId) != SpaceRole.OWNER) fail(403, "Приглашениями управляет только владелец.")
    }
    private fun normalize(email: String) = email.trim().lowercase(Locale.ROOT)
    private fun fail(code: Int, message: String): Nothing = throw ApiException(code, "demo_error", message)
    private fun newUser(id: Long, name: String, email: String, password: String): User {
        val salt = randomHex(16)
        return User(id, name, email, salt, hash(password, salt))
    }
    private fun randomHex(bytes: Int): String = ByteArray(bytes).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), 65_536, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString("") { "%02x".format(it) } }
        finally { spec.clearPassword() }
    }

    private fun seed() {
        users += newUser(3, "Гриша", "grisha@example.com", "demo12345")
        users += newUser(4, "Аня", "anna@example.com", "demo12345")
        users += newUser(5, "Олег", "oleg@example.com", "demo12345")
        spaces += Space(1, "Семья", "Europe/Moscow", ZoneId.of("Europe/Moscow"), SpaceRole.OWNER)
        spaces += Space(2, "Дача", "Asia/Yekaterinburg", ZoneId.of("Asia/Yekaterinburg"), SpaceRole.OWNER)
        memberships[1] = mutableMapOf(3L to SpaceRole.OWNER, 4L to SpaceRole.MEMBER)
        memberships[2] = mutableMapOf(5L to SpaceRole.OWNER, 3L to SpaceRole.MEMBER)
        persist()
    }

    private fun persist() = store.write(buildJsonObject {
        put("users", JsonArray(users.map { u -> buildJsonObject { put("id", u.id); put("name", u.name); put("email", u.email); put("salt", u.salt); put("hash", u.hash) } }))
        put("spaces", JsonArray(spaces.map { s -> buildJsonObject {
            put("id", s.id); put("name", s.name); put("timezone", s.timezoneId)
            put("members", buildJsonObject { memberships.getValue(s.id).forEach { (id, role) -> put(id.toString(), role.name) } })
        } }))
        put("invitations", JsonArray(invitations.map { i -> buildJsonObject {
            put("code", i.code); put("space", i.spaceId); put("expires", i.expiresAt.toEpochMilli()); put("revoked", i.revoked); put("used", i.used)
        } }))
    }.toString())

    private fun restore(raw: String) {
        val data = JsonCodec.obj(raw)
        data.getValue("users").jsonArray.forEach { el -> val u = el.jsonObject
            users += User(u.getValue("id").jsonPrimitive.long, u.getValue("name").jsonPrimitive.content, u.getValue("email").jsonPrimitive.content, u.getValue("salt").jsonPrimitive.content, u.getValue("hash").jsonPrimitive.content)
        }
        data.getValue("spaces").jsonArray.forEach { el -> val s = el.jsonObject
            val id = s.getValue("id").jsonPrimitive.long
            val zone = ZoneId.of(s.getValue("timezone").jsonPrimitive.content)
            spaces += Space(id, s.getValue("name").jsonPrimitive.content, zone.id, zone)
            memberships[id] = s.getValue("members").jsonObject.map { (key, value) -> key.toLong() to SpaceRole.valueOf(value.jsonPrimitive.content) }.toMap().toMutableMap()
        }
        data.getValue("invitations").jsonArray.forEach { el -> val i = el.jsonObject
            invitations += Invitation(i.getValue("code").jsonPrimitive.content, i.getValue("space").jsonPrimitive.long, Instant.ofEpochMilli(i.getValue("expires").jsonPrimitive.long), i.getValue("revoked").jsonPrimitive.boolean, i.getValue("used").jsonPrimitive.boolean)
        }
    }
}
