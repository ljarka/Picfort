package com.github.ljarka.picfort.ui.family

import android.content.Context
import com.github.ljarka.picfort.db.UserEntity
import com.github.ljarka.picfort.db.UsersDao
import com.github.ljarka.picfort.network.PicfortServerException
import com.github.ljarka.picfort.ui.frame.FrameSettingsStore
import com.github.ljarka.picfort.ui.login.AccessTokenStore
import com.github.ljarka.picfort.ui.server.ServerUrlStore
import com.github.ljarka.picfort.ui.timeline.SyncTimelineSource
import com.github.ljarka.picfort.ui.wall.FamilyMember
import com.github.ljarka.picfort.ui.wall.avatarColor
import com.github.ljarka.picfort.ui.wall.isTestAccount
import com.github.ljarka.picfort.ui.wall.meFirst
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Last `GET /family` response saved on disk so custom names and photos survive a cold start offline (T-032). */
interface FamilyOfflineCopy {
    fun save(raw: String)
    fun load(): String?
    fun clear()
}

@Serializable
private data class SavedFamilyProfiles(
    val server: String,
    val user: String,
    val raw: String,
)

@Singleton
class FileFamilyOfflineCopy @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: FrameSettingsStore,
    private val tokens: AccessTokenStore,
    private val json: Json,
) : FamilyOfflineCopy {
    private val file = File(context.filesDir, "family_profiles.json")

    override fun save(raw: String) {
        val saved = SavedFamilyProfiles(settings.frameUrl, tokens.userId, raw)
        runCatching { file.writeText(json.encodeToString(SavedFamilyProfiles.serializer(), saved)) }
    }

    override fun load(): String? {
        val saved = runCatching {
            json.decodeFromString(SavedFamilyProfiles.serializer(), file.readText())
        }.getOrNull() ?: return null
        if (saved.server != settings.frameUrl || saved.user != tokens.userId) {
            file.delete()
            return null
        }
        return saved.raw
    }

    override fun clear() {
        runCatching { file.delete() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FamilyModule {
    @Binds
    abstract fun backend(api: FamilyApi): FamilyBackend

    @Binds
    abstract fun offlineCopy(file: FileFamilyOfflineCopy): FamilyOfflineCopy

    @Binds
    abstract fun peopleSource(api: PeopleApi): PeopleSource
}

/**
 * Pure merge of Immich users (`UsersDao`) and Picfort server profiles (`/family`) (T-032):
 * - name = `display_name` ?: Immich name
 * - avatar = profile photo ?: Immich profile picture ?: initials on the Immich avatar color
 * - hidden = `hidden` ?: `isTestAccount(Immich name)`; the logged-in user ([myId]) is never hidden.
 */
fun mergeFamilyUsers(
    immichUsers: List<UserEntity>,
    profiles: Map<String, FamilyProfile>,
    myId: String,
    immichServerUrl: String,
): List<FamilyUserUi> {
    val base = immichServerUrl.trimEnd('/')
    return immichUsers.map { u ->
        val profile = profiles[u.id]
        val shownName = profile?.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: u.name
        val immichAvatar = if (u.hasProfileImage && base.isNotEmpty()) {
            "$base/api/users/${u.id}/profile-image?v=${u.profileChangedAt ?: 0}"
        } else {
            null
        }
        val avatarUrl = profile?.photoUrl ?: immichAvatar
        val isMe = u.id == myId
        val hidden = if (isMe) false else (profile?.hidden ?: isTestAccount(u.name))
        FamilyUserUi(
            id = u.id,
            name = shownName,
            immichName = u.name,
            avatarUrl = avatarUrl,
            immichAvatarUrl = immichAvatar,
            hasCustomPhoto = profile?.photoUrl != null,
            color = avatarColor(u.avatarColor),
            hidden = hidden,
            isMe = isMe,
        )
    }
}

/**
 * Single source of family members across the app (T-032): Wall, Shopping, Events, Receipts, Settings.
 */
@Singleton
class FamilyRepository(
    private val backend: FamilyBackend,
    private val people: PeopleSource,
    private val offline: FamilyOfflineCopy,
    private val usersDao: UsersDao,
    private val immichServerUrl: () -> String,
    private val currentUserId: () -> String,
    /** Fills [UsersDao] (the timeline sync); run when it's empty, e.g. right after login. */
    private val syncImmichUsers: suspend () -> Unit = {},
) {
    @Inject
    constructor(
        backend: FamilyBackend,
        people: PeopleSource,
        offline: FamilyOfflineCopy,
        usersDao: UsersDao,
        serverUrlStore: ServerUrlStore,
        accessTokenStore: AccessTokenStore,
        timeline: Provider<SyncTimelineSource>,
    ) : this(
        backend = backend,
        people = people,
        offline = offline,
        usersDao = usersDao,
        immichServerUrl = { serverUrlStore.serverUrl },
        currentUserId = { accessTokenStore.userId },
        syncImmichUsers = { timeline.get().refresh() },
    )

    val myId: String get() = currentUserId()
    val isConfigured: Boolean get() = backend.isConfigured

    private val profiles = MutableStateFlow<Map<String, FamilyProfile>>(loadOfflineProfiles())

    private val _supported = MutableStateFlow(backend.isConfigured)
    /** False when no Picfort server is set or the server is older than T-031 (`404` on `/family`). */
    val supported: StateFlow<Boolean> = _supported.asStateFlow()

    /** Every Immich user merged with their custom profile (visible and hidden alike). */
    val users: Flow<List<FamilyUserUi>> = combine(usersDao.all(), profiles) { immichUsers, profMap ->
        mergeFamilyUsers(
            immichUsers = immichUsers,
            profiles = profMap,
            myId = myId,
            immichServerUrl = immichServerUrl(),
        )
    }

    /** Visible family members, the logged-in user first, then sorted by shown name. */
    val members: Flow<List<FamilyMember>> = users.map { list ->
        list.filter { !it.hidden }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { it.toMember() }
            .meFirst(myId)
    }

    /** Ids of hidden users (default test accounts + explicitly hidden), whose posts the wall hides. */
    val hiddenIds: Flow<Set<String>> = users.map { list ->
        list.filter { it.hidden }.mapTo(HashSet()) { it.id }
    }

    /** Refreshes profiles from the Picfort server; keeps the offline copy on network errors. */
    suspend fun refresh(): PicfortServerException? {
        // Immich users come only from the sync stream. Before the Photos tab has ever synced
        // (a fresh install) there are none, so the Wall and Shopping had no names or avatars.
        if (usersDao.all().first().isEmpty()) runCatching { syncImmichUsers() }
        if (!backend.isConfigured) {
            _supported.value = false
            profiles.value = emptyMap()
            return null
        }
        if (profiles.value.isEmpty()) {
            val cached = loadOfflineProfiles()
            if (cached.isNotEmpty()) profiles.value = cached
        }
        return try {
            val (fresh, raw) = backend.profiles()
            profiles.value = fresh.associateBy { it.userId }
            offline.save(raw)
            _supported.value = true
            null
        } catch (e: PicfortServerException.Outdated) {
            // Old server without /family: fall back to Immich names/pictures and hide the Settings row.
            _supported.value = false
            profiles.value = emptyMap()
            offline.clear()
            e
        } catch (e: PicfortServerException) {
            if (profiles.value.isEmpty()) {
                profiles.value = loadOfflineProfiles()
            }
            e
        }
    }

    /**
     * Saves changes from the member sheet (T-032). Not optimistic: throws [PicfortServerException]
     * on failure so the sheet stays open with a snackbar.
     */
    suspend fun saveMember(
        user: FamilyUserUi,
        displayName: String,
        pickedPersonId: String?,
        clearPhoto: Boolean,
        showInFamily: Boolean,
    ) {
        if (pickedPersonId != null) {
            val jpeg = people.faceThumbnailJpeg(pickedPersonId)
            backend.uploadPhoto(user.id, jpeg)
        } else if (clearPhoto && user.hasCustomPhoto) {
            backend.deletePhoto(user.id)
        }
        val trimmedName = displayName.trim().takeIf { it.isNotEmpty() && it != user.immichName }
        val newHidden = !showInFamily
        val hiddenChanged = !user.isMe && (newHidden != user.hidden || profiles.value[user.id]?.hidden != null)
        backend.update(
            userId = user.id,
            displayName = trimmedName,
            hidden = if (hiddenChanged) newHidden else null,
            includeHidden = hiddenChanged,
        )
        refresh()
    }

    suspend fun namedPeople(): List<ImmichPersonItem> = people.namedPeople()

    private fun loadOfflineProfiles(): Map<String, FamilyProfile> {
        if (!backend.isConfigured) return emptyMap()
        val raw = offline.load() ?: return emptyMap()
        return runCatching { backend.decodeProfiles(raw).associateBy { it.userId } }.getOrDefault(emptyMap())
    }
}
