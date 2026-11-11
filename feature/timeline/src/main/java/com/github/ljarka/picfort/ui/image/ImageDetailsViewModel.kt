package com.github.ljarka.picfort.ui.image

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.github.ljarka.picfort.ui.timeline.ImageDetailsRoute
import com.github.ljarka.picfort.collections.CollectionsRepository
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.UrlProvider
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.model.FrameResult
import com.github.ljarka.picfort.core.model.FrameSender
import com.github.ljarka.picfort.ui.timeline.AssetIndex
import com.github.ljarka.picfort.ui.timeline.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import com.github.ljarka.picfort.assets.AssetDetailsRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

sealed interface FrameEvent {
    data class Message(val text: String) : FrameEvent
    data object OpenSettings : FrameEvent
}

@HiltViewModel
class ImageDetailsViewModel @Inject constructor(
    private val urlProvider: UrlProvider,
    private val repository: TimelineRepository,
    private val frameRepository: FrameSender,
    /** For video streaming: carries the Immich token and the cleartext policy. */
    val okHttpClient: OkHttpClient,
    private val detailsRepository: AssetDetailsRepository,
    private val collections: CollectionsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Album or search result to page through; null = the timeline. */
    private val source: String? = savedStateHandle.toRoute<ImageDetailsRoute>().source

    private val _details = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val details: StateFlow<DetailsState> = _details.asStateFlow()

    fun loadDetails(asset: AssetIndex) {
        _details.value = DetailsState.Loading
        viewModelScope.launch {
            _details.value = try {
                DetailsState.Loaded(detailsRepository.load(asset.assetId, asset.assetType, asset.isVideo))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DetailsState.Error(
                    if (e is java.io.IOException) "Can't reach the server" else "Couldn't load details"
                )
            }
        }
    }

    private val _frameEvents = Channel<FrameEvent>(Channel.BUFFERED)
    val frameEvents: Flow<FrameEvent> = _frameEvents.receiveAsFlow()

    private val _isSendingToFrame = MutableStateFlow(false)
    val isSendingToFrame: StateFlow<Boolean> = _isSendingToFrame.asStateFlow()

    fun getNumberOfPages(): Int = source?.let { collections.list(it).size } ?: repository.getAssetsCount()

    fun getAssetIndex(index: Int): Flow<AssetIndex> = flow {
        if (source != null) {
            val asset = collections.list(source).getOrNull(index)
            emit(AssetIndex(index, asset?.id ?: "", AssetType.REMOTE, asset?.isVideo ?: false))
            return@flow
        }
        val asset = repository.getAsset(index)
        emit(
            AssetIndex(
                index = index,
                assetId = asset?.id ?: "",
                assetType = asset?.type ?: AssetType.LOCAL,
                isVideo = asset?.isVideo ?: false,
            )
        )
    }

    fun getPreview(asset: AssetIndex): String =
        urlProvider.getPreview(asset.assetId, asset.assetType, asset.isVideo)

    fun getThumbnail(asset: AssetIndex): String =
        urlProvider.getThumbnail(asset.assetId, asset.assetType, asset.isVideo)

    fun getVideoUrl(asset: AssetIndex): String = urlProvider.getVideo(asset.assetId, asset.assetType)

    fun sendToImageFrame(assetId: String, assetType: AssetType) {
        if (_isSendingToFrame.value) return
        viewModelScope.launch {
            _isSendingToFrame.value = true
            val result = if (assetType == AssetType.LOCAL) {
                frameRepository.sendLocal(assetId)
            } else {
                frameRepository.send(assetId)
            }
            val event = when (result) {
                FrameResult.Sent -> FrameEvent.Message("Sent to frame")
                FrameResult.NotConfigured -> FrameEvent.OpenSettings
                FrameResult.Busy -> FrameEvent.Message("The frame is still refreshing, try again shortly")
                is FrameResult.TooSoon -> FrameEvent.Message(
                    result.retryAfterSeconds
                        ?.let { "Wait ${it}s before sending another photo" }
                        ?: "Too soon, wait a moment before sending another photo"
                )
                is FrameResult.Error -> FrameEvent.Message(result.message)
            }
            _isSendingToFrame.value = false
            _frameEvents.send(event)
        }
    }
}
