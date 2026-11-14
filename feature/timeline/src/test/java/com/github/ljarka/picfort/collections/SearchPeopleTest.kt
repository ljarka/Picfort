package com.github.ljarka.picfort.collections

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPeopleTest {

    private val smart = mutableListOf<SmartSearchRequest>()
    private val metadata = mutableListOf<MetadataSearchRequest>()
    private val service = object : CollectionsService {
        override suspend fun albums(): List<AlbumDto> = emptyList()
        override suspend fun createAlbum(body: CreateAlbumRequest) = error("unused")
        override suspend fun addToAlbum(albumId: String, body: BulkIdsRequest): List<BulkIdResult> = emptyList()
        override suspend fun smartSearch(body: SmartSearchRequest): SearchResponse {
            smart += body
            return SearchResponse(SearchAssetsPage(listOf(SearchAssetDto("s1"))))
        }
        override suspend fun metadataSearch(body: MetadataSearchRequest): SearchResponse {
            metadata += body
            return SearchResponse(SearchAssetsPage(listOf(SearchAssetDto("m1")), nextPage = "2"))
        }
        override suspend fun people(page: Int, size: Int, withHidden: Boolean) = when (page) {
            1 -> PeopleResponse(
                listOf(PersonDto("p1", "Ola"), PersonDto("x", ""), PersonDto("h", "Hidden", isHidden = true)),
                hasNextPage = true,
            )

            else -> PeopleResponse(listOf(PersonDto("p2", " Kuba ")))
        }
    }
    private val repository = CollectionsRepository({ service }, { error("unused") })

    @Test
    fun `people are the named, visible ones in Immich's order, from every page`() = runBlocking {
        assertEquals(listOf(SearchPerson("p1", "Ola"), SearchPerson("p2", "Kuba")), repository.people())
    }

    @Test
    fun `text alone is a smart search without people`() = runBlocking {
        repository.search(" beach ", page = 1)
        assertEquals(listOf(SmartSearchRequest("beach", 1, 100, personIds = null)), smart)
    }

    @Test
    fun `text with people is a smart search with them`() = runBlocking {
        repository.search("beach", page = 2, personIds = setOf("p1", "p2"))
        assertEquals(listOf(SmartSearchRequest("beach", 2, 100, personIds = listOf("p1", "p2"))), smart)
        assertEquals(emptyList<MetadataSearchRequest>(), metadata)
    }

    @Test
    fun `people alone are a metadata search, newest first`() = runBlocking {
        val page = repository.search("", page = 1, personIds = setOf("p1", "p2"))
        assertEquals(listOf(MetadataSearchRequest(listOf("p1", "p2"), 1, 100, "desc")), metadata)
        assertEquals(listOf("m1"), page.assets.map { it.id })
        assertEquals(2, page.nextPage)
    }

    @Test
    fun `nothing found says what was searched for`() {
        val people = listOf(SearchPerson("p1", "Ola"), SearchPerson("p2", "Kuba"), SearchPerson("p3", "Staś"))
        fun ui(query: String, vararg ids: String) =
            SearchUi(query = query, people = people, selectedPeople = ids.toSet())
        assertEquals("Nothing found for \"dog\"", ui("dog").nothingFound())
        assertEquals("No photos of Ola", ui("", "p1").nothingFound())
        assertEquals("No photos of Ola and Kuba together", ui("", "p2", "p1").nothingFound())
        assertEquals("No photos of Ola, Kuba and Staś together", ui("", "p3", "p1", "p2").nothingFound())
        assertEquals("Nothing found for \"dog\" with Ola", ui("dog", "p1").nothingFound())
    }
}

class SearchPeopleRowTest {
    private val people = (1..20).map { SearchPerson("p$it", "Person $it") } +
        listOf(SearchPerson("l", "Łukasz"), SearchPerson("z", "Zoë Kowalska"))

    @Test
    fun `the row has the ones with most photos, then those picked under All people`() {
        val row = peopleRow(people, added = listOf("p15", "l"), limit = 5)
        assertEquals(listOf("p1", "p2", "p3", "p4", "p5", "p15", "l"), row.map { it.id })
        assertEquals(people.take(12), peopleRow(people, emptyList()))
    }

    @Test
    fun `picking never moves anyone, a person from All people joins at the end`() {
        var added = emptyList<String>()
        added = addedToRow(people, added, "p3", limit = 5) // already in the row
        assertEquals(emptyList<String>(), added)
        added = addedToRow(people, added, "l", limit = 5)
        added = addedToRow(people, added, "p15", limit = 5)
        added = addedToRow(people, added, "l", limit = 5) // picked again after dropping
        assertEquals(
            listOf("p1", "p2", "p3", "p4", "p5", "l", "p15"),
            peopleRow(people, added, limit = 5).map {
                it.id
            },
        )
    }

    @Test
    fun `filter ignores case and accents`() {
        assertEquals(listOf("l"), filterPeople(people, " lukasz").map { it.id })
        assertEquals(listOf("z"), filterPeople(people, "zoe kow").map { it.id })
        assertEquals(people, filterPeople(people, ""))
        assertEquals(
            listOf("p1", "p10", "p11", "p12", "p13", "p14", "p15", "p16", "p17", "p18", "p19"),
            filterPeople(people, "son 1").map {
                it.id
            },
        )
    }
}
