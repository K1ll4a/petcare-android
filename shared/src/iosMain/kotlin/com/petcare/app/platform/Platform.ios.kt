@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.petcare.app.platform

import androidx.compose.runtime.Composable
import kotlinx.cinterop.*
import platform.CoreCrypto.*
import platform.Security.*

actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0)
    return ByteArray(size).also { bytes -> bytes.usePinned { buffer ->
        check(SecRandomCopyBytes(kSecRandomDefault, size.convert(), buffer.addressOf(0)) == errSecSuccess) { "Secure random generator failed" }
    } }
}
actual fun passwordHash(password: String, salt: String): String {
    val saltBytes = salt.encodeToByteArray()
    val result = ByteArray(32)
    saltBytes.usePinned { s -> result.usePinned { out ->
        val status = CCKeyDerivationPBKDF(
            kCCPBKDF2, password, password.encodeToByteArray().size.convert(),
            s.addressOf(0).reinterpret(), saltBytes.size.convert(), kCCPRFHmacAlgSHA256,
            65_536u, out.addressOf(0).reinterpret(), result.size.convert(),
        )
        check(status == kCCSuccess) { "Password derivation failed" }
    } }
    return result.toHex()
}
/** iOS navigation uses the visible Back buttons; there is no Android hardware back event. */
@Composable
actual fun PetCareBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
