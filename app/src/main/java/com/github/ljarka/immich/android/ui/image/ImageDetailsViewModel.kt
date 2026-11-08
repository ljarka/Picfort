package com.github.ljarka.immich.android.ui.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.immich.android.UrlProvider
import com.github.ljarka.immich.android.db.AssetType
import com.github.ljarka.immich.android.ui.frame.FrameRepository
import com.github.ljarka.immich.android.ui.frame.FrameResult
import com.github.ljarka.immich.android.ui.timeline.AssetIndex
import com.github.ljarka.immich.android.ui.timeline.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FrameEvent {
    data class Message(val text: String) : FrameEvent
    data object OpenSettings : FrameEvent
}

@HiltViewModel
class ImageDetailsViewModel @Inject constructor(
    private val urlProvider: UrlProvider,
    private val repository: TimelineRepository,
    private val frameRepository: FrameRepository,
) : ViewModel() {

    private val _frameEvents = Channel<FrameEvent>(Channel.BUFFERED)
    val frameEvents: Flow<FrameEvent> = _frameEvents.receiveAsFlow()

    private val _isSendingToFrame = MutableStateFlow(false)
    val isSendingToFrame: StateFlow<Boolean> = _isSendingToFrame.asStateFlow()

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

    fun sendToImageFrame(assetId: String, assetType: AssetType) {
        if (_isSendingToFrame.value) return
        if (assetType == AssetType.LOCAL) {
            // The frame pulls the photo from Immich, so it must be on the server.
            _frameEvents.trySend(
                FrameEvent.Message("Only photos backed up to Immich can be shown on the frame")
            )
            return
        }
        viewModelScope.launch {
            _isSendingToFrame.value = true
            val event = when (val result = frameRepository.send(assetId)) {
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
