package com.petcare.app.platform

import androidx.compose.runtime.Composable

expect fun secureRandomBytes(size: Int): ByteArray
/** Identical PBKDF2-HMAC-SHA256 parameters on both platforms; preserves existing demo accounts. */
expect fun passwordHash(password: String, salt: String): String
fun ByteArray.toHex(): String = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

@Composable
expect fun PetCareBackHandler(enabled: Boolean = true, onBack: () -> Unit)
