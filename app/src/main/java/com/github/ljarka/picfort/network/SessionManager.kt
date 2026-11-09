package com.github.ljarka.picfort.network

import com.github.ljarka.picfort.AppScope
import com.github.ljarka.picfort.sync.SyncStore
import com.github.ljarka.picfort.ui.login.AccessTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Signals that the server no longer accepts our token, so the UI can return to login. */
@Singleton
class SessionManager @Inject constructor(
    private val accessTokenStore: AccessTokenStore,
    private val syncStore: Provider<SyncStore>,
    @AppScope private val appScope: CoroutineScope,
) {
    private val _loggedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loggedOut: SharedFlow<Unit> = _loggedOut.asSharedFlow()

    /** User-initiated logout. Also drops the synced copy of the library from the phone. */
    @Synchronized
    fun logout() {
        accessTokenStore.accessToken = ""
        _loggedOut.tryEmit(Unit)
        appScope.launch(Dispatchers.IO) { syncStore.get().clear() }
    }

    /** Called from OkHttp threads; many requests may fail at once, only the first counts. */
    @Synchronized
    fun onUnauthorized() {
        if (!accessTokenStore.hasData()) return
        accessTokenStore.accessToken = ""
        _loggedOut.tryEmit(Unit)
    }
}
