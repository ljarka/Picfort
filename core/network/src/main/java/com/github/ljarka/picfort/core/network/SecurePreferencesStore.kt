package com.github.ljarka.picfort.core.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "SecurePreferencesStore"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "picfort.preferences.aes.v1"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

private val Context.securePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secure_preferences",
)

internal interface SecureCipher {
    fun encrypt(plainText: String): String
    fun decrypt(value: String): String
    fun resetKey()
}

private class KeystoreAesGcmCipher : SecureCipher {
    private val keyLock = Any()
    private var cachedKey: SecretKey? = null

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return "${b64(iv)}:${b64(ciphertext)}"
    }

    override fun decrypt(value: String): String {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2) { "Invalid encrypted payload format" }
        val iv = b64Decode(parts[0])
        val ciphertext = b64Decode(parts[1])
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    override fun resetKey() {
        synchronized(keyLock) {
            cachedKey = null
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
            cachedKey = generateKey()
        }
    }

    private fun getOrCreateKey(): SecretKey = synchronized(keyLock) {
        cachedKey?.let { return it }
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            cachedKey = existing
            return existing
        }
        return generateKey().also { cachedKey = it }
    }

    private fun generateKey(): SecretKey {
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

/**
 * Encrypted key-value storage backed by DataStore and Android Keystore.
 * Values are stored encrypted as strings and exposed synchronously to keep existing call sites simple.
 *
 * If Android Keystore loses or invalidates the key (e.g. after device restore or lockscreen reset),
 * corrupt entries are cleared and the key is recreated so the app starts clean instead of crashing (T-108).
 */
@Singleton
class SecurePreferencesStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val appScope: CoroutineScope,
    private val cipher: SecureCipher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onRecovery: (Throwable) -> Unit = { t ->
        Log.w(TAG, "Secure preferences could not be decrypted; resetting store", t)
    },
    private val onWriteError: (Throwable) -> Unit = { t ->
        Log.w(TAG, "Failed to persist secure preference", t)
    },
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @AppScope appScope: CoroutineScope,
    ) : this(
        dataStore = context.securePreferencesDataStore,
        appScope = appScope,
        cipher = KeystoreAesGcmCipher(),
    )

    private val cache = ConcurrentHashMap<String, String>()
    private val writeMutex = Mutex()

    @Volatile
    private var loaded = false
    private val loading = CompletableDeferred<Unit>()

    init {
        appScope.launch(ioDispatcher) {
            try {
                val snapshot = dataStore.data.first()
                snapshot.asMap().forEach { (prefKey, storedValue) ->
                    val encrypted = storedValue as? String
                        ?: throw IllegalStateException("Unsupported type for secure key ${prefKey.name}")
                    cache[prefKey.name] = cipher.decrypt(encrypted)
                }
            } catch (t: Throwable) {
                onRecovery(t)
                cache.clear()
                runCatching { cipher.resetKey() }
                runCatching { dataStore.edit { it.clear() } }
            } finally {
                loaded = true
                loading.complete(Unit)
            }
        }
    }

    fun getString(name: String, default: String = ""): String {
        ensureLoaded()
        return cache[name] ?: default
    }

    fun putString(name: String, value: String) {
        ensureLoaded()
        cache[name] = value
        appScope.launch(ioDispatcher) {
            writeMutex.withLock {
                val latest = cache[name] ?: return@withLock
                try {
                    val encrypted = cipher.encrypt(latest)
                    dataStore.edit { prefs -> prefs[stringPreferencesKey(name)] = encrypted }
                } catch (t: Throwable) {
                    onWriteError(t)
                }
            }
        }
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
        val done = CountDownLatch(1)
        appScope.launch(ioDispatcher) {
            try {
                loading.await()
            } finally {
                done.countDown()
            }
        }
        done.await()
    }
}
