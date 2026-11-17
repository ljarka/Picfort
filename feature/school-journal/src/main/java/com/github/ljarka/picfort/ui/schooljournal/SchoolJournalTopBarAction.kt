package com.github.ljarka.picfort.ui.schooljournal

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.github.ljarka.picfort.feature.schooljournal.R

/**
 * The school building icon `:app` puts in the top bar of Family Wall and School (spec §2).
 * Opens the journal, which is also where it is configured.
 */
@Composable
fun SchoolJournalTopBarAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_school_building), contentDescription = "School journal")
    }
}
