package com.github.ljarka.picfort.ui.family

import com.github.ljarka.picfort.db.UserEntity
import com.github.ljarka.picfort.db.UsersDao
import com.github.ljarka.picfort.network.PicfortServerException
import com.github.ljarka.picfort.ui.wall.FamilyWallUiState
import com.github.ljarka.picfort.ui.wall.WallPost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private class FakeUsersDao(initial: List<UserEntity>) : UsersDao {
    val flow = MutableStateFlow(initial)
    override suspend fun upsert(users: List<UserEntity>) {
        flow.value = users
    }
    override suspend fun delete(ids: List<String>) {}
    override suspend fun clear() {
        flow.value = emptyList()
    }
    override fun all(): Flow<List<UserEntity>> = flow
}

private class FakeOfflineCopy(var saved: String? = null) : FamilyOfflineCopy {
    override fun save(raw: String) {
        saved = raw
    }
    override fun load(): String? = saved
    override fun clear() {
        saved = null
    }
}

private class FakePeopleSource : PeopleSource {
    var people: List<ImmichPersonItem> = emptyList()
    var lastThumbPersonId: String? = null
    override suspend fun namedPeople(): List<ImmichPersonItem> = people
    override suspend fun faceThumbnailJpeg(personId: String): ByteArray {
        lastThumbPersonId = personId
        return byteArrayOf(1, 2, 3)
    }
}

private class FakeFamilyBackend(
    override var isConfigured: Boolean = true,
) : FamilyBackend {
    private val json = Json { ignoreUnknownKeys = true }
    private val base = "http://192.168.0.105:5000".toHttpUrl()
    var failWith: PicfortServerException? = null
    val map = mutableMapOf<String, FamilyProfile>()
    val uploadedPhotos = mutableMapOf<String, ByteArray>()
    val deletedPhotos = mutableListOf<String>()

    override suspend fun profiles(): Pair<List<FamilyProfile>, String> {
        failWith?.let { throw it }
        val list = map.values.toList()
        val raw = """{"profiles":[${
            list.joinToString(",") { p ->
                """{"user_id":"${p.userId}","display_name":${p.displayName?.let { "\"$it\"" } ?: "null"},"photo_url":${p.photoUrl?.let { "\"$it\"" } ?: "null"},"hidden":${p.hidden ?: "null"}}"""
            }
        }]}"""
        return list to raw
    }

    override fun decodeProfiles(raw: String): List<FamilyProfile> =
        json.decodeFromString<FamilyProfilesResponseDto>(raw).profiles.map { it.toProfile(base) }

    override suspend fun update(
        userId: String,
        displayName: String?,
        hidden: Boolean?,
        includeHidden: Boolean,
    ): FamilyProfile {
        failWith?.let { throw it }
        val existing = map[userId]
        val updated = FamilyProfile(
            userId = userId,
            displayName = displayName,
            photoUrl = existing?.photoUrl,
            hidden = if (includeHidden) hidden else existing?.hidden,
        )
        if (updated.displayName == null && updated.photoUrl == null && updated.hidden == null) {
            map.remove(userId)
        } else {
            map[userId] = updated
        }
        return updated
    }

    override suspend fun uploadPhoto(userId: String, photoJpeg: ByteArray): FamilyProfile {
        failWith?.let { throw it }
        uploadedPhotos[userId] = photoJpeg
        val existing = map[userId] ?: FamilyProfile(userId = userId)
        val updated = existing.copy(photoUrl = "http://192.168.0.105:5000/family/photo/$userId.jpg")
        map[userId] = updated
        return updated
    }

    override suspend fun deletePhoto(userId: String) {
        failWith?.let { throw it }
        deletedPhotos += userId
        val existing = map[userId] ?: return
        val updated = existing.copy(photoUrl = null)
        if (updated.displayName == null && updated.hidden == null) {
            map.remove(userId)
        } else {
            map[userId] = updated
        }
    }
}

class FamilyRepositoryTest {

    private val immichUsers = listOf(
        UserEntity("mama", "Anna Kowalska", "anna@example.com", "pink", hasProfileImage = true, profileChangedAt = 42L),
        UserEntity("tata", "Piotr Kowalski", "piotr@example.com", "blue", hasProfileImage = false, profileChangedAt = null),
        UserEntity("test", "LukaszTest", "test@example.com", "gray", hasProfileImage = false, profileChangedAt = null),
    )

    @Test
    fun `merge rules for name, avatar, hidden default and override, and logged-in user`() {
        // Without profiles: Immich names & pictures, LukaszTest hidden by default.
        val defaults = mergeFamilyUsers(
            immichUsers = immichUsers,
            profiles = emptyMap(),
            myId = "mama",
            immichServerUrl = "https://photos.example.com/",
        ).associateBy { it.id }

        assertEquals("Anna Kowalska", defaults.getValue("mama").name)
        assertEquals("https://photos.example.com/api/users/mama/profile-image?v=42", defaults.getValue("mama").avatarUrl)
        assertFalse(defaults.getValue("mama").hasCustomPhoto)
        assertFalse(defaults.getValue("mama").hidden)
        assertTrue(defaults.getValue("mama").isMe)

        assertNull(defaults.getValue("tata").avatarUrl)
        assertFalse(defaults.getValue("tata").hidden)
        assertTrue(defaults.getValue("test").hidden)

        // Custom profile: rename Mama, custom photo for Tata, unhide LukaszTest, hide Tata, try to hide me.
        val profiles = mapOf(
            "mama" to FamilyProfile("mama", displayName = "Mama", photoUrl = "http://picfort/family/photo/m.jpg", hidden = true),
            "tata" to FamilyProfile("tata", displayName = "Tata", photoUrl = "http://picfort/family/photo/t.jpg", hidden = true),
            "test" to FamilyProfile("test", displayName = null, photoUrl = null, hidden = false),
        )
        val merged = mergeFamilyUsers(
            immichUsers = immichUsers,
            profiles = profiles,
            myId = "mama",
            immichServerUrl = "https://photos.example.com",
        ).associateBy { it.id }

        assertEquals("Mama", merged.getValue("mama").name)
        assertEquals("Anna Kowalska", merged.getValue("mama").immichName)
        assertEquals("http://picfort/family/photo/m.jpg", merged.getValue("mama").avatarUrl)
        assertEquals("https://photos.example.com/api/users/mama/profile-image?v=42", merged.getValue("mama").immichAvatarUrl)
        assertTrue(merged.getValue("mama").hasCustomPhoto)
        // Logged-in user is never hidden, even if the profile says hidden = true or name has "test".
        assertFalse(merged.getValue("mama").hidden)

        assertTrue(merged.getValue("tata").hidden)
        assertFalse(merged.getValue("test").hidden)
    }

    @Test
    fun `renaming a member updates old posts on the wall`() {
        val oldPost = WallPost(
            id = 1,
            authorId = "mama",
            authorName = "Anna Kowalska",
            text = "Hello",
            photoUrl = null,
            photoWidth = null,
            photoHeight = null,
            pinned = false,
            created = Instant.EPOCH,
        )
        val merged = mergeFamilyUsers(
            immichUsers = immichUsers,
            profiles = mapOf("mama" to FamilyProfile("mama", displayName = "Mama")),
            myId = "mama",
            immichServerUrl = "https://photos.example.com",
        )
        val wallState = FamilyWallUiState(
            members = merged.filter { !it.hidden }.map { it.toMember() },
            posts = listOf(oldPost),
            myId = "mama",
        )
        assertEquals("Mama", wallState.authorOf(oldPost).name)
    }

    @Test
    fun `with no Immich users yet, refresh runs the sync that brings them`(): Unit = runBlocking {
        val dao = FakeUsersDao(emptyList())
        var syncs = 0
        val repo = FamilyRepository(
            FakeFamilyBackend(), FakePeopleSource(), FakeOfflineCopy(), dao,
            { "https://photos.example.com" }, { "mama" },
            syncImmichUsers = { syncs++; dao.upsert(immichUsers) },
        )
        repo.refresh()
        assertEquals(1, syncs)
        assertEquals(setOf("mama", "tata"), repo.members.first().map { it.id }.toSet())

        // Once the users are known, a refresh leaves the sync to the Photos tab.
        repo.refresh()
        assertEquals(1, syncs)
    }

    @Test
    fun `offline cold start keeps saved names and photos, and saveMember updates photo and fields`(): Unit = runBlocking {
        val backend = FakeFamilyBackend()
        val offline = FakeOfflineCopy()
        val people = FakePeopleSource()
        val dao = FakeUsersDao(immichUsers)
        val repo = FamilyRepository(backend, people, offline, dao, { "https://photos.example.com" }, { "mama" })
        val tataUser = repo.users.first().first { it.id == "tata" }

        // Pick a face from Immich people, rename Tata, and save.
        repo.saveMember(
            user = tataUser,
            displayName = "Tata Piotr",
            pickedPersonId = "person-tata",
            clearPhoto = false,
            showInFamily = true,
        )
        assertEquals("person-tata", people.lastThumbPersonId)
        val updatedTata = repo.users.first().first { it.id == "tata" }
        assertEquals("Tata Piotr", updatedTata.name)
        assertEquals("http://192.168.0.105:5000/family/photo/tata.jpg", updatedTata.avatarUrl)
        assertTrue(updatedTata.hasCustomPhoto)

        // Cold start offline with a new repository reading the saved offline copy.
        val offlineBackend = FakeFamilyBackend().apply {
            failWith = PicfortServerException.Unreachable("192.168.0.105")
        }
        val coldRepo = FamilyRepository(offlineBackend, people, offline, dao, { "https://photos.example.com" }, { "mama" })
        coldRepo.refresh()
        val coldMembers = coldRepo.members.first().associateBy { it.id }
        assertEquals("Tata Piotr", coldMembers.getValue("tata").name)
        assertEquals("http://192.168.0.105:5000/family/photo/tata.jpg", coldMembers.getValue("tata").avatarUrl)

        // "Use Immich picture" deletes the custom photo and reverts to Immich picture / initials.
        repo.saveMember(
            user = updatedTata,
            displayName = "",
            pickedPersonId = null,
            clearPhoto = true,
            showInFamily = true,
        )
        assertEquals(listOf("tata"), backend.deletedPhotos)
        val revertedTata = repo.users.first().first { it.id == "tata" }
        assertEquals("Piotr Kowalski", revertedTata.name)
        assertNull(revertedTata.avatarUrl)
        assertFalse(revertedTata.hasCustomPhoto)
    }

    @Test
    fun `old server without family endpoint disables support and falls back to defaults`(): Unit = runBlocking {
        val backend = FakeFamilyBackend().apply {
            failWith = PicfortServerException.Outdated("family profiles")
        }
        val offline = FakeOfflineCopy("""{"profiles":[{"user_id":"mama","display_name":"Mama"}]}""")
        val repo = FamilyRepository(
            backend = backend,
            people = FakePeopleSource(),
            offline = offline,
            usersDao = FakeUsersDao(immichUsers),
            immichServerUrl = { "https://photos.example.com" },
            currentUserId = { "mama" },
        )
        repo.refresh()
        assertFalse(repo.supported.value)
        assertNull(offline.saved)
        assertEquals("Anna Kowalska", repo.members.first().first { it.id == "mama" }.name)
        assertEquals(setOf("test"), repo.hiddenIds.first())
    }

    @Test
    fun `people parser keeps only named, non-hidden people sorted by name`() {
        val raw = """{
            "people": [
                {"id": "1", "name": "Staś", "isHidden": false},
                {"id": "2", "name": "   ", "isHidden": false},
                {"id": "3", "name": "Ola", "isHidden": false},
                {"id": "4", "name": "Hidden Person", "isHidden": true}
            ]
        }"""
        val parsed = PeopleApi.parseNamedPeople(raw, "https://photos.example.com/", Json { ignoreUnknownKeys = true })
        assertEquals(listOf("Ola", "Staś"), parsed.map { it.name })
        assertEquals("https://photos.example.com/api/people/3/thumbnail", parsed.first().thumbnailUrl)
    }
}
