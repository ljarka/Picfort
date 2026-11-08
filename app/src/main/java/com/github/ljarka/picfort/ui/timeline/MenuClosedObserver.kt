package com.github.ljarka.picfort.ui.timeline

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuClosedObserver(
    sheetState: SheetState,
    onMenuClosed: () -> Unit
) {
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue }
            .collect { state ->
                if (state == SheetValue.PartiallyExpanded) {
                    onMenuClosed
                }
            }
    }
}