package com.lingua.app.data.crypto

/**
 * Encrypts small secrets (API keys) before they are written to disk.
 *
 * Implementations must never throw on decrypt failure: returning `null` lets the UI ask the user to
 * re-enter the key, which is the correct behaviour after a device restore has invalidated the
 * hardware-backed key.
 */
interface SecretCipher {
  fun encrypt(plaintext: String): String?

  fun decrypt(blob: String): String?
}
