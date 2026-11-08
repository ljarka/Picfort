package com.github.ljarka.picfort.db

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Keep
enum class AssetType {
    LOCAL, REMOTE
}

@Entity(
    tableName = "assets",
    primaryKeys = ["bucket", "assetIndex"],
    indices = [Index(value = ["bucket", "assetIndex"])]
)
data class AssetEntity(
    @ColumnInfo(name = "bucket") val timestamp: Long,
    @ColumnInfo(name = "assetIndex") val assetIndex: Int,
    @ColumnInfo(name = "assetId") val assetId: String,
    @ColumnInfo(name = "ratio") val ratio: Double?,
    @ColumnInfo(name = "type") val type: AssetType,
    @ColumnInfo(name = "dateTaken") val dateTaken: Long? = null
)