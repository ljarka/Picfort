package com.github.ljarka.picfort.ui.image

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PinchToZoom(
    modifier: Modifier = Modifier,
    onActionUp: (scale: Float, offset: Offset) -> Unit,
    scale: Float = 1f,
    offset: Offset = Offset.Zero,
    onScaleChanged: (scale: Float) -> Unit,
    onOffsetChanged: (offset: Offset) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val animatedOffsetX by animateFloatAsState(targetValue = offset.x, label = "offsetX")
    val animatedOffsetY by animateFloatAsState(targetValue = offset.y, label = "offsetY")
    val animatedScale: Float by animateFloatAsState(targetValue = scale, label = "scale")

    var centerX by remember { mutableFloatStateOf(0f) }
    var centerY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPlaced {
                centerX = it.size.width / 2f
                centerY = it.size.height / 2f
            }
            .pointerInput(scale, offset) {
                detectTapGestures(onDoubleTap = { offset ->
                    if (scale <= ZoomStatus.Zoom1().scale) {
                        onOffsetChanged(
                            Offset(
                                x = (centerX - offset.x) * scale,
                                y = (centerY - offset.y) * scale,
                            ),
                        )
                        onScaleChanged(ZoomStatus.Zoom2().scale)
                    } else if (scale <= ZoomStatus.Zoom2().scale) {
                        onOffsetChanged(
                            Offset(
                                x = (centerX - offset.x) * scale,
                                y = (centerY - offset.y) * scale,
                            ),
                        )
                        onScaleChanged(ZoomStatus.Zoom4().scale)
                    } else {
                        onScaleChanged(ZoomStatus.Zoom1().scale)
                        onOffsetChanged(Offset.Zero)
                    }
                })
            }
            .pointerInput(scale, offset) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release) {
                            onActionUp(scale, offset)
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                translationX = animatedOffsetX
                translationY = animatedOffsetY
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun calculateOffset(value: Float, change: Float, center: Float, scale: Float): Float {
    val newValue = value + change * scale
    return if (value < 0) {
        maxOf(newValue, -center * scale)
    } else {
        minOf(newValue, center * scale)
    }
}
