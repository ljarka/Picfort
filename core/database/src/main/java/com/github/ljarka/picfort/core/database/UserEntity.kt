package com.github.ljarka.picfort.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A user of the Immich server, from the sync stream (`UsersV1`). Family Wall members (T-002). */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    /** Immich's avatar color name (primary, pink, red, …), or null for the default. */
    val avatarColor: String?,
    val hasProfileImage: Boolean,
    /** Epoch millis; changes when the profile image changes (cache key). */
    val profileChangedAt: Long?,
)

@Dao
interface UsersDao {
    @Upsert
    suspend fun upsert(users: List<UserEntity>)

    @Query("DELETE FROM users WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM users")
    suspend fun clear()

    @Query("SELECT * FROM users ORDER BY name COLLATE NOCASE, id")
    fun all(): Flow<List<UserEntity>>
}
