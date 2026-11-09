package com.github.ljarka.picfort.ui.timeline

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import com.github.ljarka.picfort.ui.timeline.layout.MonthMarker
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun FastScrollComponent(
    months: List<MonthMarker>,
    listState: LazyListState,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        var numberOfItems = months.size
        var maxOffset = with(LocalDensity.current) { (maxHeight - 208.dp).toPx() }
        var minDelta = maxOffset / numberOfItems
        var offsetY by rememberSaveable { mutableStateOf(0) }
        var index by rememberSaveable { mutableStateOf(0) }
        var coroutineScope = rememberCoroutineScope()
        var text by remember { mutableStateOf(months.first().title) }
        var currentJob by remember { mutableStateOf<Job?>(null) }
        var isDragging by remember { mutableStateOf(false) }
        val draggableState = rememberDraggableState(onDelta = { delta ->
            offsetY = (offsetY + delta).toInt()
            index = minOf(
                maxOf((offsetY / minDelta).roundToInt(), 0),
                months.size - 1
            )

            currentJob?.cancel()
            currentJob = coroutineScope.launch {
                // Rows are laid out ahead of time, so the header row is exactly where it will be.
                listState.scrollToItem(months[index].rowIndex)
                text = months[index].title
            }
        })
        LaunchedEffect(listState, isDragging, months) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect { firstItemIndex ->
                    if (!isDragging) {
                        withContext(Dispatchers.Default) {
                            val month = months.lastOrNull { it.rowIndex <= firstItemIndex }
                            if (month != null) {
                                text = month.title
                                if (listState.isScrollInProgress) {
                                    offsetY = (minDelta * months.indexOf(month)).toInt()
                                }
                            }
                        }
                    }
                }
        }
        Button(
            modifier = Modifier
                .padding(top = 112.dp)
                .graphicsLayer {
                    translationY = max(min(offsetY, maxOffset.toInt()).toFloat(), 0f)
                }
                .align(Alignment.TopEnd)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        isDragging = true
                    },
                    onDragStopped = {
                        isDragging = false
                    }
                ),
            onClick = {}

        ) {
            Text(text = text)
        }
    }
}