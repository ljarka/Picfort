package com.github.ljarka.picfort.collections

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class DeleteAlbumTest {
    private val deleted = mutableListOf<String>()
    private var refuse: Int? = null
    private val service = object : CollectionsService {
        override suspend fun albums(): List<AlbumDto> = emptyList()
        override suspend fun createAlbum(body: CreateAlbumRequest) = error("unused")
        override suspend fun deleteAlbum(albumId: String): Response<Unit> {
            refuse?.let { return Response.error(it, "{}".toResponseBody()) }
            deleted += albumId
            return Response.success(204, Unit)
        }
        override suspend fun addToAlbum(albumId: String, body: BulkIdsRequest): List<BulkIdResult> = emptyList()
        override suspend fun smartSearch(body: SmartSearchRequest) = SearchResponse()
        override suspend fun people(page: Int, size: Int, withHidden: Boolean) = PeopleResponse()
        override suspend fun metadataSearch(body: MetadataSearchRequest) = SearchResponse()
    }
    private val repository = CollectionsRepository({ service }, { error("unused") })

    private fun album(owner: String?) = AlbumDto(
        id = "a",
        albumUsers = listOfNotNull(
            owner?.let { AlbumUserDto(AlbumUserRef(it), "owner") },
            AlbumUserDto(AlbumUserRef("guest"), "editor"),
        ),
    )

    @Test
    fun `only the owner gets the delete menu, unless we can't tell`() {
        assertTrue(album("me").canDelete("me"))
        assertFalse(album("someone").canDelete("me"))
        assertFalse(album("someone").canDelete("guest"))
        assertTrue(album("someone").canDelete(""))
        assertTrue(album(null).canDelete("me"))
    }

    @Test
    fun `deleting tells the albums list`() = runBlocking {
        val told = async(start = CoroutineStart.UNDISPATCHED) { repository.deletedAlbums.first() }
        repository.deleteAlbum("a")
        assertEquals(listOf("a"), deleted)
        assertEquals("a", told.await())
    }

    @Test
    fun `a refusal says why`() = runBlocking {
        refuse = 403
        try {
            repository.deleteAlbum("a")
            fail("expected a refusal")
        } catch (e: HttpException) {
            assertEquals("Only the album's owner can delete it", deleteError(e))
        }
        assertTrue(deleted.isEmpty())
    }
}
