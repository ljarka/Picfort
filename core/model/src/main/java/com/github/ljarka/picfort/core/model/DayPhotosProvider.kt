package com.github.ljarka.picfort.core.model

import java.time.LocalDate

/** A photo or video from the family's Immich library, taken on [day] (local time where it was taken). */
data class DayPhoto(
    val id: String,
    val day: LocalDate,
    /** Width / height, null while Immich hasn't processed it. */
    val ratio: Float?,
    val thumbnailUrl: String,
    val isVideo: Boolean = false,
)

/**
 * Photos taken on given days (T-055: a birthday's photos from past years). Implemented by
 * `:feature:timeline` from its local copy of Immich, bound in `:app`.
 */
interface DayPhotosProvider {
    /**
     * Photos taken on any of [days], newest first. The full-screen viewer can then page through
     * them as the collection [source] (`ImageDetailsRoute.source`).
     */
    suspend fun photosOn(days: Set<LocalDate>, source: String): List<DayPhoto>
}
