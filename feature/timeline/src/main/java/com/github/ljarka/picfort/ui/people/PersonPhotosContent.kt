package com.github.ljarka.picfort.ui.people

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.collections.AssetGrid
import com.github.ljarka.picfort.collections.GridAsset
import com.github.ljarka.picfort.collections.LoadState
import com.github.ljarka.picfort.collections.LoadStateContent
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.avatar.MemberCarousel

/** The family carousel on Photos (T-052): one member at a time; members not linked to my Immich people are faded. */
data class PeopleFilterUi(
    val members: List<FamilyMember> = emptyList(),
    /** Member id → the person id in my Immich library (set in Family settings). */
    val myPersonIds: Map<String, String> = emptyMap(),
    val selectedId: String? = null,
) {
    val selected: FamilyMember? get() = members.firstOrNull { it.id == selectedId }
    val notLinkedIds: Set<String> get() = members.mapTo(HashSet()) { it.id } - myPersonIds.keys

    /** What a tap on [id] does: one member at a time, tap again for the whole timeline. */
    fun tap(id: String?): PeopleTap {
        if (id == null || id == selectedId) return PeopleTap.Clear
        val member = members.firstOrNull { it.id == id } ?: return PeopleTap.Ignore
        val personId = myPersonIds[id] ?: return PeopleTap.NotLinked(member)
        return PeopleTap.Show(id, personId)
    }
}

sealed interface PeopleTap {
    data object Clear : PeopleTap
    data object Ignore : PeopleTap
    data class NotLinked(val member: FamilyMember) : PeopleTap
    data class Show(val memberId: String, val personId: String) : PeopleTap
}

@Composable
fun PeopleFilterRow(
    state: PeopleFilterUi,
    onSelect: (String?) -> Unit,
    onEditMember: (String) -> Unit = {},
) {
    MemberCarousel(
        members = state.members,
        selectedId = state.selectedId,
        onSelect = onSelect,
        onEditMember = onEditMember,
        dimmedIds = state.notLinkedIds,
    )
}

/** Photos of the selected member, newest first, under the same carousel. */
@Composable
fun PersonPhotosContent(
    people: PeopleFilterUi,
    photos: LoadState<List<GridAsset>>,
    thumbnail: (GridAsset) -> String,
    modifier: Modifier = Modifier,
    onSelect: (String?) -> Unit = {},
    onEditMember: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onAssetClick: (GridAsset, Int) -> Unit = { _, _ -> },
) {
    val name = people.selected?.name.orEmpty()
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 12
        }
    }
    val count = (photos as? LoadState.Loaded)?.value?.size ?: 0
    LaunchedEffect(nearEnd, count) { if (nearEnd && count > 0) onLoadMore() }
    val header = @Composable {
        Column {
            PeopleFilterRow(people, onSelect, onEditMember)
            if (photos is LoadState.Loaded && photos.value.isNotEmpty()) {
                Text(
                    "Photos of $name",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp),
                )
            }
        }
    }
    if (photos is LoadState.Loaded && photos.value.isNotEmpty()) {
        AssetGrid(
            assets = photos.value,
            thumbnailUrl = thumbnail,
            onClick = { index -> onAssetClick(photos.value[index], index) },
            modifier = modifier,
            state = gridState,
            header = header,
        )
    } else {
        Column(modifier.fillMaxSize()) {
            header()
            LoadStateContent(photos, onRetry, Modifier.weight(1f), { it.isEmpty() }, "No photos of $name in your Immich yet") {}
        }
    }
}
