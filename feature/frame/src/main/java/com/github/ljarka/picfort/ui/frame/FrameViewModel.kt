package com.github.ljarka.picfort.ui.frame

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class FrameUi(
    val configured: Boolean = true,
    val address: String = "",
    val loading: Boolean = true,
    /** Couldn't reach the frame at all. */
    val error: String? = null,
    val busy: Boolean = false,
    val panelError: String? = null,
    val hardware: Boolean = true,
    val slideshow: SlideshowState = SlideshowState(),
    val albums: List<FrameAlbum> = emptyList(),
    val image: ImageBitmap? = null,
    val message: String? = null,
)

fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes == 60 -> "1 hour"
    minutes < 1440 -> "${minutes / 60} hours"
    else -> "1 day"
}

fun hourLabel(hour: Int): String = "%02d:00".format(hour)

private val TIME = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH)

/** "Next photo at Tue 14:30", or null. */
fun nextChangeLabel(state: SlideshowState, zone: ZoneId = ZoneId.systemDefault()): String? =
    state.nextChangeAt?.takeIf { state.mode != "off" }?.let {
        "Next photo " + TIME.format(Instant.ofEpochMilli((it * 1000).toLong()).atZone(zone))
    }

@HiltViewModel
class FrameViewModel @Inject constructor(
    private val api: FrameDashboardApi,
) : ViewModel() {
    private val _state = MutableStateFlow(FrameUi())
    val state: StateFlow<FrameUi> = _state.asStateFlow()
    private var watchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (!api.isConfigured) {
            _state.value = FrameUi(configured = false, loading = false)
            return
        }
        _state.update { it.copy(configured = true, address = api.address, loading = true, error = null) }
        viewModelScope.launch {
            try {
                val status = api.status()
                val albums = runCatching { api.albums() }.getOrDefault(emptyList())
                applyStatus(status)
                _state.update { it.copy(albums = albums, loading = false, image = loadImage()) }
                if (status.busy) watchUntilIdle()
            } catch (e: CancellationException) {
                throw e
            } catch (e: FrameException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun setMode(mode: String) {
        val albumId = _state.value.slideshow.albumId ?: _state.value.albums.firstOrNull()?.id
        if (mode == "album" && albumId == null) {
            _state.update { it.copy(message = "Create an album in Immich first") }
            return
        }
        update(if (mode == "album") mapOf("mode" to mode, "album_id" to albumId) else mapOf("mode" to mode))
    }

    fun setAlbum(id: String) = update(mapOf("mode" to "album", "album_id" to id))
    fun setInterval(minutes: Int) = update(mapOf("interval_minutes" to minutes))
    fun setQuietHours(start: Int?, end: Int?) = update(mapOf("quiet_start" to start, "quiet_end" to end))

    fun next() {
        viewModelScope.launch {
            try {
                api.next()
                _state.update { it.copy(message = "Changing photo…") }
                delay(1500)
                watchUntilIdle()
            } catch (e: FrameException) {
                _state.update { it.copy(message = e.message) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun update(fields: Map<String, Any?>) {
        viewModelScope.launch {
            try {
                val slideshow = api.updateSlideshow(fields)
                _state.update { it.copy(slideshow = slideshow) }
                if ("mode" in fields || "album_id" in fields) {
                    delay(1500) // the frame switches source right away
                    watchUntilIdle()
                }
            } catch (e: FrameException) {
                _state.update { it.copy(message = e.message) }
            }
        }
    }

    /** Polls while the e-paper refreshes (~20 s), then shows the new picture. */
    private fun watchUntilIdle() {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            repeat(30) {
                val status = runCatching { api.status() }.getOrNull() ?: return@launch
                applyStatus(status)
                if (!status.busy) {
                    _state.update { it.copy(image = loadImage() ?: it.image) }
                    return@launch
                }
                delay(3000)
            }
        }
    }

    private fun applyStatus(status: FrameStatus) = _state.update {
        it.copy(
            busy = status.busy,
            panelError = status.lastError ?: status.slideshow?.lastError,
            hardware = status.hardware,
            slideshow = status.slideshow ?: it.slideshow,
        )
    }

    private suspend fun loadImage(): ImageBitmap? = runCatching {
        api.currentImage()?.let { bytes ->
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        }
    }.getOrNull()
}
