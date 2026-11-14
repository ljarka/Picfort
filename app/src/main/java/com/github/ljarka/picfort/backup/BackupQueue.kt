package com.github.ljarka.picfort.backup

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A phone photo or video waiting for the running backup. */
data class QueuedMedia(val id: Long, val isVideo: Boolean)

/**
 * What the running backup still has to do, in its order (newest first). [uploadingId] is the file
 * being sent, [uploadProgress] how much of it (0..1). Empty when no backup runs.
 */
data class BackupQueueState(
    val pending: List<QueuedMedia> = emptyList(),
    val uploadingId: Long? = null,
    val uploadProgress: Float = 0f,
) {
    /** The file being uploaded first, then the rest in order. */
    val ordered: List<QueuedMedia>
        get() {
            val current = pending.firstOrNull { it.id == uploadingId } ?: return pending
            return listOf(current) + pending.filter { it.id != uploadingId }
        }
}

/**
 * The running backup's queue, for Settings (T-063). The worker runs in the app's process, so a
 * singleton is enough; a file leaves the queue once uploaded, found in Immich, or refused.
 */
@Singleton
class BackupQueue @Inject constructor() {
    private val _state = MutableStateFlow(BackupQueueState())
    val state: StateFlow<BackupQueueState> = _state.asStateFlow()

    fun start(items: List<QueuedMedia>) {
        _state.value = BackupQueueState(pending = items)
    }

    fun uploading(id: Long) = _state.update { it.copy(uploadingId = id, uploadProgress = 0f) }

    fun progress(id: Long, fraction: Float) = _state.update {
        if (it.uploadingId == id) it.copy(uploadProgress = fraction.coerceIn(0f, 1f)) else it
    }

    fun finished(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val gone = ids.toSet()
        _state.update { s ->
            s.copy(
                pending = s.pending.filter { it.id !in gone },
                uploadingId = s.uploadingId.takeIf { it !in gone },
                uploadProgress = if (s.uploadingId in gone) 0f else s.uploadProgress,
            )
        }
    }

    fun clear() {
        _state.value = BackupQueueState()
    }
}
