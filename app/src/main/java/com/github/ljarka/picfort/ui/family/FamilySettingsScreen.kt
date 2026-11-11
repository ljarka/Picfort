package com.github.ljarka.picfort.ui.family

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.github.ljarka.picfort.ui.wall.FamilyMember
import com.github.ljarka.picfort.ui.wall.MemberAvatar

/** One Immich user in Settings -> Family (T-032). */
data class FamilyUserUi(
    val id: String,
    /** Shown name (`display_name` ?: [immichName]). */
    val name: String,
    val immichName: String,
    val avatarUrl: String?,
    val immichAvatarUrl: String? = null,
    val hasCustomPhoto: Boolean = false,
    val color: Color,
    val hidden: Boolean = false,
    val isMe: Boolean = false,
) {
    fun toMember() = FamilyMember(id = id, name = name, avatarUrl = avatarUrl, color = color)
}

data class FamilySettingsUi(
    val users: List<FamilyUserUi> = emptyList(),
    val isLoading: Boolean = false,
    val editing: FamilyMemberEditUi? = null,
    val peoplePicker: PeoplePickerUi? = null,
    val message: String? = null,
) {
    /** Visible members first, then hidden accounts last. */
    val orderedUsers: List<FamilyUserUi>
        get() = users.sortedWith(
            compareBy<FamilyUserUi> { it.hidden }
                .thenBy { if (it.isMe) 0 else 1 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySettingsScreen(onBack: () -> Unit) {
    val viewModel = hiltViewModel<FamilySettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() }
    }
    state.editing?.let { edit ->
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissMember,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            FamilyMemberSheetContent(
                state = edit,
                onNameChange = viewModel::updateName,
                onChooseFromPeople = viewModel::openPeoplePicker,
                onUseImmichPhoto = viewModel::useImmichPhoto,
                onShowInFamilyChange = viewModel::updateShowInFamily,
                onCancel = viewModel::dismissMember,
                onSave = viewModel::saveMember,
            )
        }
    }
    state.peoplePicker?.let { picker ->
        PeoplePickerSheet(
            state = picker,
            onDismiss = viewModel::dismissPeoplePicker,
            onQueryChange = viewModel::updatePeopleQuery,
            onSelect = viewModel::selectPerson,
        )
    }
    FamilySettingsContent(
        state = state,
        onBack = onBack,
        onSelectUser = viewModel::openMember,
        onMessageShown = viewModel::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySettingsContent(
    state: FamilySettingsUi,
    onBack: () -> Unit = {},
    onSelectUser: (FamilyUserUi) -> Unit = {},
    onMessageShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading && state.users.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(state.orderedUsers, key = { it.id }) { user ->
                    FamilyUserRow(user = user, onClick = { onSelectUser(user) })
                }
            }
        }
    }
}

@Composable
private fun FamilyUserRow(user: FamilyUserUi, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(if (user.hidden) Modifier.alpha(0.55f) else Modifier),
        leadingContent = {
            MemberAvatar(member = user.toMember(), size = 44.dp)
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.name != user.immichName) {
                    Text(
                        " · ${user.immichName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = when {
            user.hidden -> ({
                Text(
                    "Hidden",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            })
            user.isMe -> ({
                Text(
                    "You",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            })
            else -> null
        },
    )
}

/** State of the member-edit bottom sheet (T-032). */
data class FamilyMemberEditUi(
    val user: FamilyUserUi,
    val displayName: String = if (user.name != user.immichName) user.name else "",
    val previewAvatarUrl: String? = user.avatarUrl,
    val hasCustomPhoto: Boolean = user.hasCustomPhoto,
    val pickedPersonId: String? = null,
    val clearPhoto: Boolean = false,
    val showInFamily: Boolean = !user.hidden,
    val saving: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilyMemberSheetContent(
    state: FamilyMemberEditUi,
    onNameChange: (String) -> Unit = {},
    onChooseFromPeople: () -> Unit = {},
    onUseImmichPhoto: () -> Unit = {},
    onShowInFamilyChange: (Boolean) -> Unit = {},
    onCancel: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    val effectiveName = state.displayName.trim().ifEmpty { state.user.immichName }
    val previewMember = FamilyMember(
        id = state.user.id,
        name = effectiveName,
        avatarUrl = state.previewAvatarUrl,
        color = state.user.color,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        Text(state.user.immichName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.displayName,
            onValueChange = onNameChange,
            label = { Text("Name") },
            placeholder = { Text(state.user.immichName) },
            supportingText = { Text("Leave empty to use the Immich name (${state.user.immichName})") },
            singleLine = true,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Photo",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MemberAvatar(member = previewMember, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(onClick = onChooseFromPeople, enabled = !state.saving) {
                    Text("Choose from Immich people")
                }
                if (state.hasCustomPhoto) {
                    TextButton(onClick = onUseImmichPhoto, enabled = !state.saving) {
                        Text("Use Immich picture")
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!state.user.isMe && !state.saving) {
                        Modifier.clickable { onShowInFamilyChange(!state.showInFamily) }
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show in the family", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (state.user.isMe) "Your own account is always shown"
                    else "Hidden accounts and their posts are left out",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = state.showInFamily,
                onCheckedChange = onShowInFamilyChange,
                enabled = !state.user.isMe && !state.saving,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !state.saving) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave, enabled = !state.saving) {
                Text(if (state.saving) "Saving…" else "Save")
            }
        }
    }
}
