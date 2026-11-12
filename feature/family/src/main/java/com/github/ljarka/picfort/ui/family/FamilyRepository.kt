package com.github.ljarka.picfort.ui.family

import android.content.Context
import com.github.ljarka.picfort.core.database.UserEntity
import com.github.ljarka.picfort.core.database.UsersDao
import com.github.ljarka.picfort.core.model.BirthDate
import com.github.ljarka.picfort.core.network.PicfortServerException
import com.github.ljarka.picfort.core.network.FrameSettingsStore
import com.github.ljarka.picfort.core.network.AccessTokenStore
import com.github.ljarka.picfort.core.network.ServerUrlStore
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.isTestAccount
import com.github.ljarka.picfort.core.model.meFirst
import com.github.ljarka.picfort.core.model.ImmichUsersSync
import com.github.ljarka.picfort.core.model.FamilyMemberProvider
import com.github.ljarka.picfort.core.ui.avatar.avatarColor
import com.github.ljarka.picfort.core.ui.avatar.isChosenAvatarColor
import dagger.hilt.android.qualifiers.ApplicationContext
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

/**
 * Pure merge of Immich users (`UsersDao`) and Picfort server profiles (`/family`) (T-032):
 * - name = `display_name` ?: Immich name
 * - avatar = profile photo ?: Immich profile picture (unless `no_photo`) ?: initials on the Immich avatar color
 * - hidden = `hidden` ?: `isTestAccount(Immich name)`; the logged-in user ([myId]) is never hidden.
 */
fun mergeFamilyUsers(
    immichUsers: List<UserEntity>,
    profiles: Map<String, FamilyProfile>,
    myId: String,
    immichServerUrl: String,
): List<FamilyUserUi> {
    val base = immichServerUrl.trimEnd('/')
    val immichIds = immichUsers.mapTo(HashSet()) { it.id }
    val manualProfiles = profiles.values.filter { it.manual || !immichIds.contains(it.userId) }
    // Members without a picked color get aperture colors in id order: distinct and the same on every phone.
    val fallbackIndex = (immichUsers.filterNot { isChosenAvatarColor(it.avatarColor) }.map { it.id } +
        manualProfiles.map { it.userId }).sorted().withIndex().associate { (i, id) -> id to i }
    val immichList = immichUsers.map { u ->
        val profile = profiles[u.id]
        val shownName = profile?.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: u.name
        val immichAvatar = if (u.hasProfileImage && base.isNotEmpty()) {
            "$base/api/users/${u.id}/profile-image?v=${u.profileChangedAt ?: 0}"
        } else {
            null
        }
        val noPhoto = profile?.noPhoto == true
        val avatarUrl = profile?.photoUrl ?: immichAvatar.takeUnless { noPhoto }
        val isMe = u.id == myId
        val hidden = if (isMe) false else (profile?.hidden ?: isTestAccount(u.name))
        FamilyUserUi(
            id = u.id,
            name = shownName,
            immichName = u.name,
            avatarUrl = avatarUrl,
            immichAvatarUrl = immichAvatar,
            hasCustomPhoto = profile?.photoUrl != null,
            noPhoto = noPhoto,
            color = avatarColor(u.avatarColor, fallbackIndex[u.id] ?: 0),
            hidden = hidden,
            isMe = isMe,
            isImmichUser = true,
            birthDate = BirthDate.parse(profile?.birthDate),
        )
    }

    val manualList = manualProfiles.map { p ->
        FamilyUserUi(
            id = p.userId,
            name = p.displayName ?: "Member",
            immichName = "",
            avatarUrl = p.photoUrl,
            immichAvatarUrl = null,
            hasCustomPhoto = p.photoUrl != null,
            color = avatarColor(null, fallbackIndex[p.userId] ?: 0),
            hidden = p.hidden ?: false,
            isMe = false,
            isImmichUser = false,
            birthDate = BirthDate.parse(p.birthDate),
        )
    }

    return immichList + manualList
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
) : FamilyMemberProvider {
    @Inject
    constructor(
        backend: FamilyBackend,
        people: PeopleSource,
        offline: FamilyOfflineCopy,
        usersDao: UsersDao,
        serverUrlStore: ServerUrlStore,
        accessTokenStore: AccessTokenStore,
        usersSync: ImmichUsersSync,
    ) : this(
        backend = backend,
        people = people,
        offline = offline,
        usersDao = usersDao,
        immichServerUrl = { serverUrlStore.serverUrl },
        currentUserId = { accessTokenStore.userId },
        syncImmichUsers = { usersSync.sync() },
    )

    override val myId: String get() = currentUserId()
    val isConfigured: Boolean get() = backend.isConfigured

    private val profiles = MutableStateFlow<Map<String, FamilyProfile>>(loadOfflineProfiles())

    private val _supported = MutableStateFlow(backend.isConfigured)
    /** False when no Picfort server is set or the server is older than T-031 (`404` on `/family`). */
    val supported: StateFlow<Boolean> = _supported.asStateFlow()

    private val _canAddMembers = MutableStateFlow(backend.isConfigured)

    private val _myPersonIds = MutableStateFlow<Map<String, String>>(emptyMap())
    override val myPersonIds: Flow<Map<String, String>> = _myPersonIds
    val canAddMembers: StateFlow<Boolean> = _canAddMembers.asStateFlow()

    /** Every Immich user merged with their custom profile (visible and hidden alike). */
    val users: Flow<List<FamilyUserUi>> = combine(usersDao.all(), profiles) { immichUsers, profMap ->
        mergeFamilyUsers(
            immichUsers = immichUsers,
            profiles = profMap,
            myId = myId,
            immichServerUrl = immichServerUrl(),
        )
    }

    /** Visible Immich members (people who can post or write). */
    override val members: Flow<List<FamilyMember>> = users.map { list ->
        list.filter { !it.hidden && it.isImmichUser }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { it.toMember() }
            .meFirst(myId)
    }

    /** All visible family members (Immich and manual): Wall avatar row, Shopping, Events, pickers. */
    override val allMembers: Flow<List<FamilyMember>> = users.map { list ->
        list.filter { !it.hidden }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { it.toMember() }
            .meFirst(myId)
    }

    /** Ids of hidden users (default test accounts + explicitly hidden), whose posts the wall hides. */
    override val hiddenIds: Flow<Set<String>> = users.map { list ->
        list.filter { it.hidden }.mapTo(HashSet()) { it.id }
    }

    /** Refreshes profiles from the Picfort server; keeps the offline copy on network errors. */
    override suspend fun refresh(): PicfortServerException? {
        // Immich users come only from the sync stream. Before the Photos tab has ever synced
        // (a fresh install) there are none, so the Wall and Shopping had no names or avatars.
        if (usersDao.all().first().isEmpty()) runCatching { syncImmichUsers() }
        if (!backend.isConfigured) {
            _supported.value = false
            _canAddMembers.value = false
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
            // Optional: a server before T-052 has no /family/people; keep what we had.
            runCatching { backend.myPeople() }.onSuccess { _myPersonIds.value = it }
            _supported.value = true
            null
        } catch (e: PicfortServerException.Outdated) {
            // Old server without /family: fall back to Immich names/pictures and hide the Settings row.
            _supported.value = false
            _canAddMembers.value = false
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
     * Saves changes from the member sheet (T-032, T-034). Not optimistic: throws [PicfortServerException]
     * on failure so the sheet stays open with a snackbar.
     */
    suspend fun saveMember(
        user: FamilyUserUi,
        displayName: String,
        pickedPersonId: String? = null,
        photoBytes: ByteArray? = null,
        clearPhoto: Boolean = false,
        showInFamily: Boolean = true,
        birthDate: BirthDate? = null,
        clearBirthDate: Boolean = false,
        /** "Clear" on an Immich user: initials instead of the account picture. */
        noPhoto: Boolean = user.noPhoto,
    ) {
        if (pickedPersonId != null) {
            val jpeg = people.faceThumbnailJpeg(pickedPersonId)
            backend.uploadPhoto(user.id, jpeg)
            // The chosen face is who this member is in my photos (T-052).
            backend.linkPerson(user.id, pickedPersonId)
        } else if (photoBytes != null) {
            backend.uploadPhoto(user.id, photoBytes)
        } else if (clearPhoto && user.hasCustomPhoto) {
            backend.deletePhoto(user.id)
        }
        val trimmedName = displayName.trim().takeIf { it.isNotEmpty() && (!user.isImmichUser || it != user.immichName) }
        val newHidden = !showInFamily
        val hiddenChanged = !user.isMe && (newHidden != user.hidden || profiles.value[user.id]?.hidden != null)
        val birthDateStr = if (clearBirthDate) null else birthDate?.toIsoString()
        val birthDateChanged = clearBirthDate || (birthDate != user.birthDate)
        val today = java.time.LocalDate.now().toString()

        backend.update(
            userId = user.id,
            displayName = trimmedName,
            hidden = if (hiddenChanged) newHidden else null,
            includeHidden = hiddenChanged,
            birthDate = birthDateStr,
            includeBirthDate = birthDateChanged,
            today = today,
            // A new photo resets the flag on the server.
            noPhoto = noPhoto.takeIf { user.isImmichUser && pickedPersonId == null && photoBytes == null && it != user.noPhoto },
        )
        refresh()
    }

    suspend fun addMember(
        displayName: String,
        birthDate: BirthDate? = null,
        photoBytes: ByteArray? = null,
        pickedPersonId: String? = null,
    ) {
        val today = java.time.LocalDate.now().toString()
        val birthDateStr = birthDate?.toIsoString()
        try {
            // Fetch the face first: a failure then doesn't leave a member without the chosen photo.
            val photo = pickedPersonId?.let { people.faceThumbnailJpeg(it) } ?: photoBytes
            val created = backend.addMember(displayName, birthDateStr, today)
            if (photo != null) {
                backend.uploadPhoto(created.userId, photo)
            }
            if (pickedPersonId != null) backend.linkPerson(created.userId, pickedPersonId)
            refresh()
        } catch (e: PicfortServerException.Outdated) {
            _canAddMembers.value = false
            throw e
        }
    }

    suspend fun deleteMember(userId: String) {
        backend.deleteMember(userId)
        refresh()
    }

    suspend fun namedPeople(): List<ImmichPersonItem> = people.namedPeople()

    private fun loadOfflineProfiles(): Map<String, FamilyProfile> {
        if (!backend.isConfigured) return emptyMap()
        val raw = offline.load() ?: return emptyMap()
        return runCatching { backend.decodeProfiles(raw).associateBy { it.userId } }.getOrDefault(emptyMap())
    }
}
