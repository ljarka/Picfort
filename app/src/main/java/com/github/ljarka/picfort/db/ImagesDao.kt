package com.github.ljarka.picfort.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ImagesDao {
    @Query("SELECT * FROM assets where bucket = :bucket order by assetIndex")
    suspend fun getAssets(bucket: Long): List<AssetEntity>

    @Query("SELECT * FROM assets order by bucket asc")
    suspend fun getAllAssets(): List<AssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(assets: List<AssetEntity>)

    @Delete
    suspend fun deleteAsset(asset: AssetEntity)

    @Query("DELETE FROM assets where bucket = :bucket")
    suspend fun deleteAssets(bucket: Long)

    @Query("SELECT * FROM month_buckets order by timestamp desc")
    fun getMonthBuckets(): Flow<List<MonthBucketEntity>>

    @Query("SELECT * FROM assets where bucket = :bucket and assetIndex = :assetIndex")
    suspend fun getAsset(bucket: Long, assetIndex: Int): AssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonthBuckets(bucket: List<MonthBucketEntity>)

    @Query("DELETE FROM month_buckets")
    suspend fun deleteMonthBuckets()

    @Update
    suspend fun updateBucket(monthBucketEntity: MonthBucketEntity): Int

    @Query("SELECT * FROM month_buckets where timestamp = :timestamp")
    suspend fun getBucket(timestamp: Long): MonthBucketEntity?
}