package com.github.ljarka.picfort.db

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** Where an asset lives: in Immich, or only on this phone. */
@Serializable
@Keep
enum class AssetType {
    LOCAL, REMOTE
}
