package com.github.ljarka.picfort.core.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Size of the floating bottom navigation bar drawn by `:app`'s shell over every tab root.
 * Tab content draws edge-to-edge behind it, so anything pinned to the bottom must clear it.
 */
object FloatingTabBar {
    val Height: Dp = 64.dp
    val MarginVertical: Dp = 8.dp

    /** Space the bar takes above the system navigation bar: the bar plus its margins. */
    val Clearance: Dp = Height + MarginVertical * 2
}

/**
 * Lifts a tab root's floating action button above the floating navigation bar.
 * The Scaffold already keeps the FAB above the system navigation bar.
 */
fun Modifier.aboveFloatingTabBar(): Modifier = padding(bottom = FloatingTabBar.Clearance)
