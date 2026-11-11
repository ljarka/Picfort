package com.github.ljarka.picfort.core.model

/**
 * Contract to trigger Immich user synchronization when user cache is empty.
 */
fun interface ImmichUsersSync {
    suspend fun sync()
}
