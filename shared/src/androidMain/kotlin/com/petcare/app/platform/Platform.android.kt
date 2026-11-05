package com.petcare.app.platform

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
actual fun passwordHash(password: String, salt: String): String {
    val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(Charsets.UTF_8), 65_536, 256)
    return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.toHex() }
    finally { spec.clearPassword() }
}
@Composable
actual fun PetCareBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
