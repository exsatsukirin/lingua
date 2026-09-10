package com.lingua.app.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SecretCipher] backed by a non-exportable AES-256 key in the AndroidKeyStore.
 *
 * Wire format of the produced blob: `base64(iv || ciphertext || tag)`.
 *
 * Every failure path (key invalidated by a restore, corrupted blob, provider misbehaving) degrades to
 * `null` instead of throwing, so a broken keystore can never crash the app.
 */
class KeystoreSecretCipher(
  private val alias: String = DEFAULT_ALIAS,
  private val keyStoreProvider: () -> KeyStore = ::loadAndroidKeyStore,
) : SecretCipher {

  override fun encrypt(plaintext: String): String? =
    runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey() ?: return null)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
      }
      .getOrNull()

  override fun decrypt(blob: String): String? =
    runCatching {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        if (raw.size <= IV_LENGTH_BYTES) return null
        val iv = raw.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = raw.copyOfRange(IV_LENGTH_BYTES, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey() ?: return null, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
      }
      .getOrNull()

  private fun secretKey(): SecretKey? {
    val keyStore = keyStoreProvider()
    (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    return runCatching {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
          KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        )
        generator.generateKey()
      }
      .getOrNull()
  }

  companion object {
    const val DEFAULT_ALIAS = "lingua_api_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    private fun loadAndroidKeyStore(): KeyStore =
      KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
  }
}

/** Volatile in-memory stand-in for tests and previews. */
class InMemorySecretCipher : SecretCipher {
  private val store = mutableMapOf<String, String>()
  private var counter = 0

  override fun encrypt(plaintext: String): String {
    val token = "mem-${counter++}"
    store[token] = plaintext
    return token
  }

  override fun decrypt(blob: String): String? = store[blob]
}
