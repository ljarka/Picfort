package com.github.ljarka.picfort.core.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.ljarka.picfort.core.network.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "picfort.preferences.aes.v1"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

private val Context.securePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_preferences")

/**
 * Encrypted key-value storage backed by DataStore and Android Keystore.
 * Values are stored encrypted as strings and exposed synchronously to keep existing call sites simple.
 */
@Singleton
class SecurePreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
) {
    private val dataStore = context.securePreferencesDataStore
    private val cache = ConcurrentHashMap<String, String>()
    private val key by lazy { getOrCreateKey() }

    @Volatile
    private var loaded = false
    private val loading = CompletableDeferred<Unit>()

    init {
        appScope.launch(Dispatchers.IO) {
            try {
                val snapshot = dataStore.data.first()
                snapshot.asMap().forEach { (prefKey, storedValue) ->
                    val encrypted = storedValue as? String
                        ?: throw IllegalStateException("Unsupported type for secure key ${prefKey.name}")
                    cache[prefKey.name] = decrypt(encrypted)
                }
                loaded = true
                loading.complete(Unit)
            } catch (t: Throwable) {
                loading.completeExceptionally(t)
            }
        }
    }

    fun getString(name: String, default: String = ""): String {
        ensureLoaded()
        return cache[name] ?: default
    }

    fun putString(name: String, value: String) {
        ensureLoaded()
        val encrypted = encrypt(value)
        blockingIo {
            dataStore.edit { prefs -> prefs[stringPreferencesKey(name)] = encrypted }
        }
        cache[name] = value
    }

    fun getBoolean(name: String, default: Boolean = false): Boolean {
        ensureLoaded()
        return when (cache[name]?.lowercase(Locale.US)) {
            "1", "true" -> true
            "0", "false" -> false
            null -> default
            else -> default
        }
    }

    fun putBoolean(name: String, value: Boolean) {
        putString(name, if (value) "1" else "0")
    }

    private fun ensureLoaded() {
        if (loaded) return
        var failure: Throwable? = null
        val done = CountDownLatch(1)
        appScope.launch(Dispatchers.IO) {
            try {
                loading.await()
            } catch (t: Throwable) {
                failure = t
            } finally {
                done.countDown()
            }
        }
        done.await()
        if (!loaded) {
            throw IllegalStateException("Couldn't load secure preferences", failure)
        }
    }

    private fun blockingIo(block: suspend () -> Unit) {
        var failure: Throwable? = null
        val done = CountDownLatch(1)
        appScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (t: Throwable) {
                failure = t
            } finally {
                done.countDown()
            }
        }
        done.await()
        failure?.let { throw IllegalStateException("Couldn't save secure preference", it) }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return "${b64(iv)}:${b64(ciphertext)}"
    }

    private fun decrypt(value: String): String {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2) { "Invalid encrypted payload format" }
        val iv = b64Decode(parts[0])
        val ciphertext = b64Decode(parts[1])
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun b64Decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
