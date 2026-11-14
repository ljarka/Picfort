package com.github.ljarka.picfort.backup

import com.github.ljarka.picfort.ui.settings.backupDone
import com.github.ljarka.picfort.ui.settings.countInImmich
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupQueueTest {

    @Test
    fun `the file being uploaded comes first, finished files leave`() {
        val queue = BackupQueue()
        queue.start((1L..4L).map { QueuedMedia(it, isVideo = false) })
        queue.uploading(3)
        queue.progress(3, 0.5f)
        assertEquals(listOf(3L, 1L, 2L, 4L), queue.state.value.ordered.map { it.id })
        assertEquals(0.5f, queue.state.value.uploadProgress)

        queue.finished(listOf(3L, 1L))
        assertEquals(listOf(2L, 4L), queue.state.value.ordered.map { it.id })
        assertNull(queue.state.value.uploadingId)

        queue.progress(3, 0.9f) // late progress of a finished file is ignored
        assertEquals(0f, queue.state.value.uploadProgress)
        queue.clear()
        assertEquals(emptyList<QueuedMedia>(), queue.state.value.ordered)
    }

    @Test
    fun `items in Immich count the backup's links and the timeline's matches once`() {
        // 1, 2 linked by Picfort's backup; 2, 3 matched by the timeline (e.g. uploaded by Immich's app).
        assertEquals(3, countInImmich(onPhone = 10, linked = listOf(1L, 2L), onServer = setOf(2L, 3L)))
        assertEquals(2, countInImmich(onPhone = 10, linked = listOf(1L, 2L), onServer = null))
        assertEquals(1, countInImmich(onPhone = 1, linked = listOf(1L, 2L), onServer = setOf(3L)))
    }

    @Test
    fun `progress follows the queue between the worker's batch reports`() {
        val queue = BackupQueueState(pending = (1L..90L).map { QueuedMedia(it, false) })
        assertEquals(61, backupDone(BackupStatus.Running(done = 50, total = 151), queue))
        assertEquals(50, backupDone(BackupStatus.Running(done = 50, total = 151), BackupQueueState()))
    }
}
