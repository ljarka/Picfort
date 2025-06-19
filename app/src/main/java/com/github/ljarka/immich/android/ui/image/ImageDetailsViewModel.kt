package com.github.ljarka.immich.android.ui.image

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.immich.android.UrlProvider
import com.github.ljarka.immich.android.db.AssetType
import com.github.ljarka.immich.android.ui.timeline.AssetIndex
import com.github.ljarka.immich.android.ui.timeline.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImageDetailsViewModel @Inject constructor(
    private val urlProvider: UrlProvider,
    private val repository: TimelineRepository,
    private val imageFrameService: ImageFrameService,
) : ViewModel() {

    fun getNumberOfPages(): Int = repository.getAssetsCount()

    fun getAssetIndex(index: Int): Flow<AssetIndex> = flow {
        val asset = repository.getAsset(index)
        emit(
            AssetIndex(
                index = index,
                assetId = asset?.id ?: "",
                assetType = asset?.type ?: AssetType.LOCAL
            )
        )
    }

    fun getPreview(assetId: String, assetType: AssetType): String =
        urlProvider.getPreview(assetId, assetType)

    fun getThumbnail(assetId: String, assetType: AssetType): String =
        urlProvider.getThumbnail(assetId, assetType)

    fun sendToImageFrame(assetId: String) {
        viewModelScope.launch {
            runCatching {
                val response = imageFrameService.sendToImageFrame(assetId)
                Log.d("lukjar", "response code " + response.code())
            }.onFailure {
                Log.d("lukjar", "response code " + it.message)
            }
        }
    }
}