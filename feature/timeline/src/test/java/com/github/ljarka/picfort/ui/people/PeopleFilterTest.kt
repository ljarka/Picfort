package com.github.ljarka.picfort.ui.people

import androidx.compose.ui.graphics.Color
import com.github.ljarka.picfort.collections.AlbumDto
import com.github.ljarka.picfort.collections.BulkIdResult
import com.github.ljarka.picfort.collections.BulkIdsRequest
import com.github.ljarka.picfort.collections.CollectionsRepository
import com.github.ljarka.picfort.collections.CollectionsService
import com.github.ljarka.picfort.collections.CreateAlbumRequest
import com.github.ljarka.picfort.collections.MetadataSearchRequest
import com.github.ljarka.picfort.collections.SearchAssetDto
import com.github.ljarka.picfort.collections.SearchAssetsPage
import com.github.ljarka.picfort.collections.SearchResponse
import com.github.ljarka.picfort.collections.PeopleResponse
import com.github.ljarka.picfort.collections.SmartSearchRequest
import com.github.ljarka.picfort.core.model.FamilyMember
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleFilterTest {
    private val ola = FamilyMember("ola", "Ola", null, Color.Green)
    private val kuba = FamilyMember("kuba", "Kuba", null, Color.Blue, isImmichUser = false)
    private val ui = PeopleFilterUi(listOf(ola, kuba), myPersonIds = mapOf("ola" to "person-ola"))

    @Test
    fun `members without my person are faded and a tap asks to link them`() {
        assertEquals(setOf("kuba"), ui.notLinkedIds)
        assertEquals(PeopleTap.NotLinked(kuba), ui.tap("kuba"))
    }

    @Test
    fun `one member at a time, tapping again goes back to the timeline`() {
        assertEquals(PeopleTap.Show("ola", "person-ola"), ui.tap("ola"))
        val selected = ui.copy(selectedId = "ola")
        assertEquals(ola, selected.selected)
        assertEquals(PeopleTap.Clear, selected.tap("ola"))
        assertEquals(PeopleTap.Clear, selected.tap(null))
        assertEquals(PeopleTap.Ignore, ui.tap("gone"))
    }

    @Test
    fun `photos of a person come from a metadata search, newest first, paged`() = runBlocking {
        val requests = mutableListOf<MetadataSearchRequest>()
        val service = object : CollectionsService {
            override suspend fun albums(): List<AlbumDto> = emptyList()
            override suspend fun createAlbum(body: CreateAlbumRequest) = error("unused")
            override suspend fun addToAlbum(albumId: String, body: BulkIdsRequest): List<BulkIdResult> = emptyList()
            override suspend fun smartSearch(body: SmartSearchRequest) = SearchResponse()
            override suspend fun people(page: Int, size: Int, withHidden: Boolean) = PeopleResponse()
            override suspend fun metadataSearch(body: MetadataSearchRequest): SearchResponse {
                requests += body
                return SearchResponse(
                    SearchAssetsPage(listOf(SearchAssetDto("a1"), SearchAssetDto("v1", type = "VIDEO")), nextPage = "2"),
                )
            }
        }
        val repository = CollectionsRepository({ service }, { error("unused") })
        val page = repository.personAssets("person-ola", page = 1)
        assertEquals(listOf(MetadataSearchRequest(listOf("person-ola"), 1, 100, "desc")), requests)
        assertEquals(listOf("a1", "v1"), page.assets.map { it.id })
        assertEquals(true, page.assets[1].isVideo)
        assertEquals(2, page.nextPage)
    }
}
