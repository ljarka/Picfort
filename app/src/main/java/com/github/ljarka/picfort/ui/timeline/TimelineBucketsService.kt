package com.github.ljarka.picfort.ui.timeline

import retrofit2.http.GET
import retrofit2.http.Query

interface TimelineBucketsService {

    @GET("/api/timeline/buckets")
    suspend fun getTimeBuckets(
        @Query("size") size: String = "MONTH",
        @Query("withPartners") withPartners: Boolean = true,
        @Query("isArchived") isArchived: Boolean = false,
        @Query("withStacked") isStacked: Boolean = true,
        @Query("visibility") visibility: String? = "timeline",
        /** Restrict to one album (then withPartners must be false); null = whole timeline. */
        @Query("albumId") albumId: String? = null,
    ): List<TimeBucket>

    @GET("/api/timeline/bucket")
    suspend fun getBucket(
        @Query("size") size: String = "MONTH",
        @Query("timeBucket") timeBucket: String,
        @Query("withPartners") withPartners: Boolean = true,
        @Query("isArchived") isArchived: Boolean = false,
        @Query("withStacked") isStacked: Boolean = true,
        @Query("visibility") visibility: String? = "timeline",
        /** Restrict to one album (then withPartners must be false); null = whole timeline. */
        @Query("albumId") albumId: String? = null,
    ): BucketResponse

}