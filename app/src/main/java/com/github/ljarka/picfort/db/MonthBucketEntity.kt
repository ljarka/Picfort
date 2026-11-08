package com.github.ljarka.picfort.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "month_buckets",
    primaryKeys = ["timestamp"],
    indices = [Index(value = ["timestamp"])]
)
data class MonthBucketEntity(
    @ColumnInfo(name = "timestamp") val timestamp: Long = 0,
    @ColumnInfo(name = "count") val count: Int = 0,
    @ColumnInfo(name = "rowsNumber") val rowsNumber: Int? = null,
    @ColumnInfo(name = "index") val index: Int = 0,
    @ColumnInfo(name = "lastUpdate") val lastUpdate: Long = System.currentTimeMillis(),
)