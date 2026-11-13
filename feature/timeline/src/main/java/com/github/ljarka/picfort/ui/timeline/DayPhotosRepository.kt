package com.github.ljarka.picfort.ui.timeline

import com.github.ljarka.picfort.UrlProvider
import com.github.ljarka.picfort.collections.CollectionsRepository
import com.github.ljarka.picfort.collections.GridAsset
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.database.ImagesDatabase
import com.github.ljarka.picfort.core.database.RemoteAssetEntity
import com.github.ljarka.picfort.core.model.DayPhoto
import com.github.ljarka.picfort.core.model.DayPhotosProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Photos taken on given days, from the local copy of the Immich timeline (mine and partners'
 * shown in my timeline). Works offline; photos only on the phone are not included (T-055).
 */
@Singleton
class DayPhotosRepository @Inject constructor(
    database: ImagesDatabase,
    private val urls: UrlProvider,
    private val collections: CollectionsRepository,
) : DayPhotosProvider {
    private val dao = database.remoteAssetsDao()

    override suspend fun photosOn(days: Set<LocalDate>, source: String): List<DayPhoto> {
        if (days.isEmpty()) return emptyList()
        val userId = dao.syncState()?.userId ?: return emptyList()
        val from = days.min().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val monthDays = days.map { "%02d-%02d".format(it.monthValue, it.dayOfMonth) }.distinct()
        val photos = dao.takenOnMonthDays(userId, from, monthDays).mapNotNull { it.toDayPhoto(days) }
        collections.putList(source, photos.map { GridAsset(it.id, it.isVideo) })
        return photos
    }

    private fun RemoteAssetEntity.toDayPhoto(days: Set<LocalDate>): DayPhoto? {
        // localDateTime is wall-clock time stored as if UTC.
        val day = Instant.ofEpochMilli(localDateTime ?: return null).atOffset(ZoneOffset.UTC).toLocalDate()
        if (day !in days) return null
        val w = width
        val h = height
        val ratio = if (w != null && h != null && w > 0 && h > 0) w.toFloat() / h else null
        return DayPhoto(id, day, ratio, urls.getThumbnail(id, AssetType.REMOTE, isVideo), isVideo)
    }
}
