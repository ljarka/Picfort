package com.github.ljarka.picfort.ui.timeline

import android.content.Context
import android.util.Log
import com.github.ljarka.picfort.AppScope
import com.github.ljarka.picfort.UrlProvider
import com.github.ljarka.picfort.db.AssetEntity
import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.db.ImagesDatabase
import com.github.ljarka.picfort.db.MonthBucketEntity
import com.github.ljarka.picfort.local.LocalImagesProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Calendar
import java.util.Locale
import java.util.SortedMap
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class AssetIndex(
    val index: Int,
    val assetId: String,
    val assetType: AssetType,
)

private const val TAG = "TimelineRepository"

@Singleton
class TimelineRepository @Inject constructor(
    @AppScope val coroutineScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val imagesDatabase: ImagesDatabase,
    private val timelineBucketsService: TimelineBucketsService,
    private val urlProvider: UrlProvider,
) {
    private val timeBucketsCache: MutableStateFlow<SortedMap<Long, TimeBucketUi>> =
        MutableStateFlow(sortedMapOf(reverseOrder()))
    private val itemsCache = mutableMapOf<Long, List<AssetUi>>()
    private val imagesDao by lazy { imagesDatabase.imagesDao() }

    fun getAssetsCount(): Int = timeBucketsCache.value.values.sumOf { it.count }

    /** Cached buckets; call [refresh] to sync with the server and the phone. */
    fun getTimeBuckets(): Flow<List<TimeBucketUi>> = loadBucketsFromDb()

    private suspend fun loadBucketsInfoFromServerAndDisk(): List<MonthBucketEntity> {
        val localImagesProvider = LocalImagesProvider()
        val dbBuckets = imagesDao.getMonthBuckets().firstOrNull()
        val dbRowsNumbers = dbBuckets?.associate { it.timestamp to it.rowsNumber }

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.add(Calendar.MONTH, 1)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        val buckets = mutableListOf<TimeBucket>()
        val localBuckets = (0..23) // two years from now interval
            .map {
                calendar.add(Calendar.MONTH, -1)
                val time = calendar.toInstant().toString()
                TimeBucket(
                    timeBucket = time,
                    count = localImagesProvider.getImagesCountForMonth(
                        context = context,
                        month = YearMonth.of(
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH) + 1,
                        ),
                    )
                )
            }.associateBy { parseDate(it.timeBucket).toEpochMilli() }

        val remoteBuckets = timelineBucketsService.getTimeBuckets()
            .associateBy { parseDate(it.timeBucket).toEpochMilli() }

        localBuckets.values.forEach {
            val timestamp = parseDate(it.timeBucket).toEpochMilli()
            val count = it.count + (remoteBuckets[timestamp]?.count ?: 0)
            if (count > 0) {
                buckets.add(
                    TimeBucket(
                        timeBucket = it.timeBucket,
                        count = count
                    )
                )
            }
        }

        remoteBuckets.values.forEach {
            val timestamp = parseDate(it.timeBucket).toEpochMilli()
            if (!localBuckets.containsKey(timestamp)) {
                val count = it.count + (localBuckets[timestamp]?.count ?: 0)

                if (count > 0) {
                    buckets.add(
                        TimeBucket(
                            timeBucket = it.timeBucket,
                            count = count
                        )
                    )
                }
            }
        }

        return buckets
            .map {
                val timestamp = parseDate(it.timeBucket).toEpochMilli()

                MonthBucketEntity(
                    timestamp = timestamp,
                    count = it.count,
                    rowsNumber = dbRowsNumbers?.get(timestamp)
                )
            }.runningFoldIndexed(
                MonthBucketEntity()
            ) { index, acc, bucket ->
                MonthBucketEntity(
                    timestamp = bucket.timestamp,
                    index = acc.index + acc.count,
                    count = bucket.count,
                    rowsNumber = bucket.rowsNumber,
                )
            }.drop(1)
    }

    private fun loadBucketsFromDb() = imagesDao.getMonthBuckets()
        .map { buckets ->
            buckets.map { bucketItem ->
                TimeBucketUi(
                    timeStamp = bucketItem.timestamp,
                    count = bucketItem.count,
                    formattedDate = formatDate(Instant.ofEpochMilli(bucketItem.timestamp)),
                    numberOfRows = bucketItem.rowsNumber ?: 0,
                    index = bucketItem.index,
                    lastUpdate = bucketItem.lastUpdate,
                    items = timeBucketsCache.value[bucketItem.timestamp]?.items?.takeIf { it.isNotEmpty() }
                        ?: itemsCache.remove(bucketItem.timestamp) ?: emptyList(),
                )
            }
        }.onEach {
            timeBucketsCache.value = it.associateBy { it.timeStamp }.toSortedMap()
        }

    fun getAsset(bucket: Long, position: Int): AssetUi? {
        return timeBucketsCache.value[bucket]?.items?.getOrNull(position)
    }

    fun parseDate(timeBucket: String): Instant {
        return try {
            // Try parsing as full ISO timestamp with timezone
            Instant.parse(timeBucket)
        } catch (e1: DateTimeParseException) {
            try {
                // Try parsing as LocalDateTime (assumes no timezone, uses UTC)
                val ldt = LocalDateTime.parse(timeBucket)
                ldt.toInstant(ZoneOffset.UTC)
            } catch (e2: DateTimeParseException) {
                // Try parsing as just a date (assumes midnight UTC)
                val ld = LocalDate.parse(timeBucket)
                ld.atStartOfDay().toInstant(ZoneOffset.UTC)
            }
        }
    }

    /** Asset at a flat timeline [index], or null if out of range or it can't be loaded. */
    suspend fun getAsset(index: Int): AssetUi? {
        if (index < 0) return null
        return withContext(Dispatchers.Default) {
            val sortedValues = timeBucketsCache.value.values.sortedByDescending { it.timeStamp }
            var itemsCount = 0
            val bucketsBefore = sortedValues.takeWhile {
                itemsCount += it.count
                itemsCount <= index
            }
            val assetsBefore = bucketsBefore.sumOf { it.count }
            val currentBucket = sortedValues.getOrNull(bucketsBefore.size) ?: return@withContext null

            if (currentBucket.items.isEmpty()) {
                try {
                    fetchAssets(currentBucket.timeStamp)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't load bucket ${currentBucket.timeStamp}", e)
                    return@withContext null
                }
            }

            val bucket = timeBucketsCache.value[currentBucket.timeStamp]
            bucket?.items?.getOrNull(index - assetsBefore)
        }
    }

    /** Loads a bucket's assets. Throws if they aren't cached and the server can't be reached. */
    suspend fun fetchAssets(bucket: Long): Boolean {
        if (!coroutineContext.isActive) return false
        val dbAssets = imagesDao.getAssets(bucket)
        val assets = if (dbAssets.isEmpty()) {
            if (!coroutineContext.isActive) return false
            updateAssets(bucket)
            imagesDao.getAssets(bucket)
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                // Background refresh of cached data: failing (e.g. offline) is fine.
                try {
                    if (coroutineContext.isActive) updateAssets(bucket)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Background update of bucket $bucket failed", e)
                }
            }
            dbAssets
        }.map { it.toAssetUi() }

        if (!coroutineContext.isActive) return false
        val items = adjustSpans(assets)
        itemsCache.put(bucket, items)
        val rows = (items.sumOf { it.span } + 3) / 4
        val dbBucket = imagesDao.getBucket(bucket)
        dbBucket?.let {
            imagesDao.updateBucket(
                dbBucket.copy(
                    rowsNumber = rows,
                    lastUpdate = System.currentTimeMillis()
                )
            )
        }
        return true
    }

    private suspend fun updateAssets(bucket: Long) {
        val timeInstant = Instant.ofEpochMilli(bucket)

        val bucketResponse = timelineBucketsService.getBucket(
            timeBucket = timeInstant.toString()
        )
        val remoteAssets = bucketResponse.id.mapIndexed { index, id ->
            AssetEntity(
                timestamp = bucket,
                assetId = id,
                ratio = bucketResponse.ratio[index],
                dateTaken = parseDate(bucketResponse.fileCreatedAt[index]).toEpochMilli(),
                assetIndex = 0,
                type = AssetType.REMOTE,
            )
        }

        // Bucket keys are midnight UTC on the 1st; read the month in UTC, or zones
        // west of UTC would see the previous month.
        val localAssets = LocalImagesProvider()
            .getImagesForMonth(context, YearMonth.from(timeInstant.atZone(ZoneOffset.UTC)))
            .map {
                AssetEntity(
                    timestamp = bucket,
                    assetId = it.id.toString(),
                    ratio = (it.width?.toDouble() ?: 1.0) / (it.height?.toDouble() ?: 1.0),
                    type = AssetType.LOCAL,
                    dateTaken = it.dateTaken,
                    assetIndex = 0,
                )
            }

        updateDbAssets(remoteAssets + localAssets)
    }

    private suspend fun updateDbAssets(assets: List<AssetEntity>) {
        imagesDao.insertAssets(
            assets = assets
                .sortedByDescending { it.dateTaken }
                .mapIndexed { index, asset -> asset.copy(assetIndex = index) }
        )
    }

    private fun formatDate(instant: Instant): String {
        val date = instant.atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("MMMM, yyyy", Locale.ENGLISH)
        return date.format(formatter)
    }

    private fun calculateSpan(ratio: Double) = when {
        ratio >= 1.5 -> 4
        ratio >= 1.35 -> 3
        ratio >= 1 -> 2
        else -> 1
    }

    private fun adjustSpans(assets: List<AssetUi>): List<AssetUi> {
        val result = mutableListOf<AssetUi>()
        val spanMax = 4
        var spanSum = 0
        assets.forEach {
            spanSum = spanSum + it.span
            if (spanSum == spanMax) {
                result.add(it)
                spanSum = 0
            } else if (spanSum < spanMax) {
                result.add(it)
            } else {
                val span = it.span - (spanSum - spanMax)

                if (span == 0) {
                    result.add(it.copy(span = 1))
                    spanSum = 1
                } else {
                    result.add(it.copy(span = span))
                    spanSum = 0
                }
            }
        }
        return result
    }

    private fun AssetEntity.toAssetUi(): AssetUi {
        return AssetUi(
            id = assetId,
            url = urlProvider.getThumbnail(assetId, type),
            span = ratio?.let { calculateSpan(it) } ?: 1,
            type = type,
        )
    }

    /** Syncs bucket counts with the server and the phone. Throws on network errors. */
    suspend fun refresh() {
        imagesDao.insertMonthBuckets(loadBucketsInfoFromServerAndDisk())
    }
}