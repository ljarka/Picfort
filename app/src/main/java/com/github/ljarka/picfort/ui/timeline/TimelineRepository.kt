package com.github.ljarka.picfort.ui.timeline

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.github.ljarka.picfort.AppScope
import com.github.ljarka.picfort.UrlProvider
import com.github.ljarka.picfort.db.AssetEntity
import com.github.ljarka.picfort.db.BackupDao
import com.github.ljarka.picfort.local.monthRangeMillis
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
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
    val isVideo: Boolean = false,
)

private const val TAG = "TimelineRepository"

@Singleton
class TimelineRepository @Inject constructor(
    @AppScope val coroutineScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val imagesDatabase: ImagesDatabase,
    private val timelineBucketsService: TimelineBucketsService,
    private val urlProvider: UrlProvider,
    private val backupDao: BackupDao,
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

        val localBuckets = (0..23) // two years from now interval
            .map {
                calendar.add(Calendar.MONTH, -1)
                val time = calendar.toInstant().toString()
                val month = YearMonth.of(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
                val range = monthRangeMillis(month, ZoneId.systemDefault())
                // Phone photos already in Immich are shown once, as the Immich copy.
                // This is an estimate; fetchAssets corrects the count when the month loads.
                val onPhone = localImagesProvider.getImagesCountForMonth(context = context, month = month)
                val backedUp = backupDao.countBackedUp(range.first, range.last + 1)
                parseBucketDate(time).toEpochMilli() to (onPhone - backedUp).coerceAtLeast(0)
            }.toMap()

        val remoteBuckets = timelineBucketsService.getTimeBuckets()
            .associate { parseBucketDate(it.timeBucket).toEpochMilli() to it.count }

        return mergeMonthCounts(localBuckets, remoteBuckets).map { (timestamp, count) ->
            MonthBucketEntity(timestamp = timestamp, count = count, rowsNumber = dbRowsNumbers?.get(timestamp))
        }
    }

    private fun loadBucketsFromDb() = imagesDao.getMonthBuckets()
        .map { buckets ->
            // Flat start index of each month, derived from current counts so that a
            // corrected count can't leave later months pointing at the wrong items.
            var start = 0
            buckets.map { bucketItem ->
                val index = start.also { start += bucketItem.count }
                TimeBucketUi(
                    timeStamp = bucketItem.timestamp,
                    count = bucketItem.count,
                    formattedDate = formatDate(Instant.ofEpochMilli(bucketItem.timestamp)),
                    numberOfRows = bucketItem.rowsNumber ?: 0,
                    index = index,
                    lastUpdate = bucketItem.lastUpdate,
                    items = timeBucketsCache.value[bucketItem.timestamp]?.items?.takeIf { it.isNotEmpty() }
                        ?: itemsCache.remove(bucketItem.timestamp) ?: emptyList(),
                )
            }
        }.onEach {
            timeBucketsCache.value = it.associateBy { it.timeStamp }.toSortedMap()
        }

    fun parseDate(timeBucket: String): Instant = parseBucketDate(timeBucket)

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
        val rows = rowCount(items.map { it.span })
        val dbBucket = imagesDao.getBucket(bucket)
        dbBucket?.let {
            imagesDao.updateBucket(
                dbBucket.copy(
                    count = items.size, // the estimate from refresh() can be off
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
                isVideo = bucketResponse.isImage.getOrNull(index) == false,
                durationMs = parseImmichDuration(bucketResponse.duration.getOrNull(index)),
                thumbhash = bucketResponse.thumbhash.getOrNull(index),
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
                    isVideo = it.isVideo,
                    durationMs = it.durationMs,
                )
            }

        // Hide phone copies of photos that are already in this Immich month.
        val remoteIds = remoteAssets.mapTo(HashSet()) { it.assetId }
        val backedUp = localAssets.map { it.assetId.toLong() }.chunked(500)
            .flatMap { backupDao.get(it) }
            .filter { it.remoteId in remoteIds }
            .mapTo(HashSet()) { it.localId.toString() }
        val phoneOnly = localAssets.filter { it.assetId !in backedUp }

        updateDbAssets(bucket, remoteAssets + phoneOnly)
    }

    /**
     * Replaces the bucket's rows. Rows are keyed by (bucket, index), so a plain
     * upsert would leave stale rows behind when a month shrinks (e.g. after a delete).
     */
    private suspend fun updateDbAssets(bucket: Long, assets: List<AssetEntity>) {
        imagesDatabase.withTransaction {
            imagesDao.deleteAssets(bucket)
            imagesDao.insertAssets(
                assets = assets
                    .sortedByDescending { it.dateTaken }
                    .mapIndexed { index, asset -> asset.copy(assetIndex = index) }
            )
        }
    }

    /**
     * Re-downloads the given months and the bucket counts, and drops their
     * in-memory items so the timeline shows the new contents.
     */
    suspend fun reloadBuckets(buckets: Set<Long>) {
        refresh()
        buckets.forEach { bucket ->
            updateAssets(bucket)
            timeBucketsCache.value[bucket]?.let { cached ->
                timeBucketsCache.value = timeBucketsCache.value.toSortedMap(reverseOrder()).apply {
                    put(bucket, cached.copy(items = emptyList()))
                }
            }
            fetchAssets(bucket)
        }
    }

    private fun formatDate(instant: Instant): String {
        val date = instant.atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("MMMM, yyyy", Locale.ENGLISH)
        return date.format(formatter)
    }

    private fun adjustSpans(assets: List<AssetUi>): List<AssetUi> =
        assets.zip(adjustSpans(assets.map { it.span })) { asset, span ->
            if (asset.span == span) asset else asset.copy(span = span)
        }

    private fun AssetEntity.toAssetUi(): AssetUi {
        return AssetUi(
            id = assetId,
            url = urlProvider.getThumbnail(assetId, type, isVideo),
            span = ratio?.let { calculateSpan(it) } ?: 1,
            type = type,
            isVideo = isVideo,
            durationMs = durationMs,
            placeholder = thumbhash?.let(::thumbHashBitmap),
        )
    }

    /** Syncs bucket counts with the server and the phone. Throws on network errors. */
    suspend fun refresh() {
        val buckets = loadBucketsInfoFromServerAndDisk()
        // Replace, so months that became empty disappear (row counts are carried over).
        imagesDatabase.withTransaction {
            imagesDao.deleteMonthBuckets()
            imagesDao.insertMonthBuckets(buckets)
        }
    }
}