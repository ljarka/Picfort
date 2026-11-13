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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.github.ljarka.picfort.core.model.BirthDate
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.avatar.MemberAvatar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** One family member in Settings -> Family (T-032, T-034). */
data class FamilyUserUi(
    val id: String,
    /** Shown name (`display_name` ?: [immichName]). */
    val name: String,
    val immichName: String = "",
    val avatarUrl: String?,
    val immichAvatarUrl: String? = null,
    val hasCustomPhoto: Boolean = false,
    /** "Clear" was used: initials even though Immich has a picture. */
    val noPhoto: Boolean = false,
    val color: Color,
    val hidden: Boolean = false,
    val isMe: Boolean = false,
    val isImmichUser: Boolean = true,
    val birthDate: BirthDate? = null,
) {
    fun toMember() = FamilyMember(
        id = id,
        name = name,
        avatarUrl = avatarUrl,
        color = color,
        isImmichUser = isImmichUser,
        birthDate = birthDate,
    )
}

data class AddPersonUi(
    val name: String = "",
    val birthDate: BirthDate? = null,
    val previewAvatarUrl: String? = null,
    val photoBytes: ByteArray? = null,
    /** Immich person whose face becomes the photo (T-034 follow-up: manual members too). */
    val pickedPersonId: String? = null,
    val saving: Boolean = false,
)

data class FamilySettingsUi(
    val users: List<FamilyUserUi> = emptyList(),
    val isLoading: Boolean = false,
    val canAddMembers: Boolean = true,
    val editing: FamilyMemberEditUi? = null,
    val addingPerson: AddPersonUi? = null,
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
                onClearPhoto = viewModel::clearPhoto,
                onShowInFamilyChange = viewModel::updateShowInFamily,
                onBirthDateChange = viewModel::updateMemberBirthDate,
                onAskDelete = viewModel::askDeleteMember,
                onCancelDelete = viewModel::cancelDeleteMember,
                onConfirmDelete = viewModel::confirmDeleteMember,
                onCancel = viewModel::dismissMember,
                onSave = viewModel::saveMember,
            )
        }
    }

    state.addingPerson?.let { adding ->
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissAddPerson,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AddPersonSheetContent(
                state = adding,
                onNameChange = viewModel::updateAddPersonName,
                onBirthDateChange = viewModel::updateAddPersonBirthDate,
                onChooseFromPeople = viewModel::openPeoplePicker,
                onRemovePhoto = viewModel::removeAddPersonPhoto,
                onCancel = viewModel::dismissAddPerson,
                onSave = viewModel::saveAddPerson,
            )
        }
    }

    state.peoplePicker?.let { picker ->
        PeoplePickerSheet(
            state = picker,
            onDismiss = viewModel::dismissPeoplePicker,
            onQueryChange = viewModel::updatePeopleQuery,
            onSelect = viewModel::selectPerson,
            onSelectProfilePicture = viewModel::useImmichPhoto,
        )
    }

    FamilySettingsContent(
        state = state,
        onBack = onBack,
        onSelectUser = viewModel::openMember,
        onAddPerson = viewModel::openAddPerson,
        onMessageShown = viewModel::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySettingsContent(
    state: FamilySettingsUi,
    onBack: () -> Unit = {},
    onSelectUser: (FamilyUserUi) -> Unit = {},
    onAddPerson: () -> Unit = {},
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
                actions = {
                    if (state.canAddMembers) {
                        IconButton(onClick = onAddPerson) {
                            Icon(Icons.Default.Add, contentDescription = "Add person")
                        }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.isImmichUser) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            "Immich",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (user.isImmichUser && user.name != user.immichName && user.immichName.isNotEmpty()) {
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
        supportingContent = user.birthDate?.let { bday ->
            {
                Text(
                    "🎂 " + bday.formatDisplay(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
