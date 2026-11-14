package com.github.ljarka.picfort.ui.family

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.core.model.BirthDate
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.avatar.MemberAvatar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** State of the member-edit bottom sheet (T-032, T-034). */
data class FamilyMemberEditUi(
    val user: FamilyUserUi,
    /** Prefilled with the shown name; saving the Immich name unchanged stores no override. */
    val displayName: String = user.name,
    val previewAvatarUrl: String? = user.avatarUrl,
    val hasCustomPhoto: Boolean = user.hasCustomPhoto,
    val pickedPersonId: String? = null,
    val clearPhoto: Boolean = false,
    val noPhoto: Boolean = user.noPhoto,
    val showInFamily: Boolean = !user.hidden,
    /** On the School tab (T-068); [wasInSchool] is what the server has, for the delete warning. */
    val goesToSchool: Boolean = false,
    val wasInSchool: Boolean = goesToSchool,
    val birthDate: BirthDate? = user.birthDate,
    val clearBirthDate: Boolean = false,
    val confirmDelete: Boolean = false,
    val saving: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilyMemberSheetContent(
    state: FamilyMemberEditUi,
    onNameChange: (String) -> Unit = {},
    onChooseFromPeople: () -> Unit = {},
    /** Initials instead of any picture. */
    onClearPhoto: () -> Unit = {},
    onShowInFamilyChange: (Boolean) -> Unit = {},
    onGoesToSchoolChange: (Boolean) -> Unit = {},
    onBirthDateChange: (BirthDate?) -> Unit = {},
    onAskDelete: () -> Unit = {},
    onCancelDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onCancel: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    val titleText = if (state.user.isImmichUser) state.user.immichName else state.user.name
    val effectiveName = state.displayName.trim().ifEmpty {
        if (state.user.isImmichUser) state.user.immichName else state.user.name
    }
    val previewMember = FamilyMember(
        id = state.user.id,
        name = effectiveName,
        avatarUrl = state.previewAvatarUrl,
        color = state.user.color,
        isImmichUser = state.user.isImmichUser,
        birthDate = state.birthDate,
    )

    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Delete ${state.user.name}?") },
            text = { Text("Her birthday event is deleted too.") },
            confirmButton = {
                Button(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        Text(titleText, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.displayName,
            onValueChange = onNameChange,
            label = { Text("Name") },
            placeholder = { Text(if (state.user.isImmichUser) state.user.immichName else "Name") },
            supportingText = if (state.user.isImmichUser) {
                { Text("Leave empty to use the Immich name (${state.user.immichName})") }
            } else {
                null
            },
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
            // Immich people (face recognition) work for every member, with or without an account.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(onClick = onChooseFromPeople, enabled = !state.saving) {
                    Text("Choose from Immich")
                }
                if (state.previewAvatarUrl != null) {
                    TextButton(onClick = onClearPhoto, enabled = !state.saving) {
                        Text("Clear")
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        BirthdaySection(
            birthDate = state.birthDate,
            onBirthDateChange = onBirthDateChange,
            enabled = !state.saving,
        )
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
                    },
                )
                .padding(vertical = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show in the family", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (state.user.isMe) {
                        "Your own account is always shown"
                    } else {
                        "Hidden accounts and their posts are left out"
                    },
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!state.saving) Modifier.clickable { onGoesToSchoolChange(!state.goesToSchool) } else Modifier,
                )
                .padding(vertical = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Goes to school", style = MaterialTheme.typography.bodyLarge)
                val removing = state.wasInSchool && !state.goesToSchool
                Text(
                    if (removing) "Their timetable is deleted when you save" else "Shown on the School tab",
                    style = MaterialTheme.typography.bodyMedium,
                    color = with(MaterialTheme.colorScheme) { if (removing) error else onSurfaceVariant },
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = state.goesToSchool, onCheckedChange = onGoesToSchoolChange, enabled = !state.saving)
        }
        if (!state.user.isImmichUser) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onAskDelete,
                enabled = !state.saving,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete member")
            }
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
            Button(
                onClick = onSave,
                enabled =
                    !state.saving && (state.user.isImmichUser || state.displayName.isNotBlank()),
            ) {
                Text(if (state.saving) "Saving…" else "Save")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPersonSheetContent(
    state: AddPersonUi,
    onNameChange: (String) -> Unit = {},
    onBirthDateChange: (BirthDate?) -> Unit = {},
    onChooseFromPeople: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
    onCancel: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        Text("Add family member", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text("Name *") },
            placeholder = { Text("e.g. Babcia Zosia") },
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MemberAvatar(
                member = FamilyMember(
                    id = "new",
                    name = state.name.trim().ifEmpty { "?" },
                    avatarUrl = state.previewAvatarUrl,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    isImmichUser = false,
                ),
                size = 56.dp,
            )
            Spacer(Modifier.width(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(onClick = onChooseFromPeople, enabled = !state.saving) {
                    Text("Choose from Immich")
                }
                if (state.pickedPersonId != null) {
                    TextButton(onClick = onRemovePhoto, enabled = !state.saving) {
                        Text("Clear")
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        BirthdaySection(
            birthDate = state.birthDate,
            onBirthDateChange = onBirthDateChange,
            enabled = !state.saving,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Added to family without an Immich account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !state.saving) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSave,
                enabled = state.name.isNotBlank() && !state.saving,
            ) {
                Text(if (state.saving) "Adding…" else "Add person")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdaySection(birthDate: BirthDate?, onBirthDateChange: (BirthDate?) -> Unit, enabled: Boolean = true) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Birthday",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { showDatePicker = true },
                enabled = enabled,
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(birthDate?.formatDisplay() ?: "Set birth date")
            }
            if (birthDate != null) {
                TextButton(
                    onClick = { onBirthDateChange(null) },
                    enabled = enabled,
                ) {
                    Text("Remove")
                }
            }
        }
    }

    if (showDatePicker) {
        val initialYear = birthDate?.year ?: 2000
        val initialMonth = birthDate?.month ?: 1
        val initialDay = birthDate?.day ?: 1
        val initialDate = LocalDate.of(initialYear, initialMonth, initialDay)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onBirthDateChange(
                                BirthDate(
                                    month = date.monthValue,
                                    day = date.dayOfMonth,
                                    year = date.year,
                                ),
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
