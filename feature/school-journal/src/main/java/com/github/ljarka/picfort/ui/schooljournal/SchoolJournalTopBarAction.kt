package com.github.ljarka.picfort.ui.schooljournal

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.core.model.pollEvery
import com.github.ljarka.picfort.feature.schooljournal.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** How often the dot is rechecked while Wall or School is on screen (spec §6). */
private const val NEWS_POLL_MILLIS = 5 * 60 * 1000L

@HiltViewModel
class SchoolJournalBadgeViewModel @Inject constructor(private val news: SchoolJournalNews) : ViewModel() {
    val hasNew: StateFlow<Boolean> = news.news.map { it.any }
        .stateIn(viewModelScope, SharingStarted.Eagerly, news.news.value.any)

    /** Right away (also on return from the journal), then every 5 min, while started. */
    suspend fun keepFresh() = pollEvery(intervalMillis = NEWS_POLL_MILLIS) { news.refresh() }
}

/**
 * The school building icon `:app` puts in the top bar of Family Wall and School (spec §2), with a
 * red dot when something is new for this Immich user. Opens the journal, also to configure it.
 */
@Composable
fun SchoolJournalTopBarAction(onClick: () -> Unit) {
    val viewModel = hiltViewModel<SchoolJournalBadgeViewModel>()
    val hasNew by viewModel.hasNew.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() }
    }
    SchoolJournalIcon(hasNew = hasNew, onClick = onClick)
}

/** The icon alone, for screenshot tests. */
@Composable
fun SchoolJournalIcon(hasNew: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (hasNew) Badge(containerColor = MaterialTheme.colorScheme.error) }) {
            Icon(
                painterResource(R.drawable.ic_school_building),
                contentDescription = if (hasNew) "School journal, new items" else "School journal",
            )
        }
    }
}
