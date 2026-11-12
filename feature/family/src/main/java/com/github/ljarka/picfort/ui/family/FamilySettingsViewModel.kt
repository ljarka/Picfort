package com.github.ljarka.picfort.ui.family

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.core.model.BirthDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val POLL_MILLIS = 60_000L

@HiltViewModel
class FamilySettingsViewModel @Inject constructor(
    private val repository: FamilyRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private var initialEditUserId: String? = savedState.get<String>("editUserId")

    private val _state = MutableStateFlow(FamilySettingsUi(isLoading = true))
    val state: StateFlow<FamilySettingsUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.users.collect { users ->
                _state.update { s ->
                    val autoEdit = initialEditUserId?.let { targetId ->
                        users.firstOrNull { it.id == targetId }?.also { initialEditUserId = null }
                    }
                    s.copy(
                        users = users,
                        isLoading = false,
                        editing = s.editing ?: autoEdit?.let { FamilyMemberEditUi(user = it) },
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.canAddMembers.collect { canAdd ->
                _state.update { it.copy(canAddMembers = canAdd) }
            }
        }
    }

    suspend fun keepFresh() {
        while (true) {
            val error = repository.refresh()
            if (error != null && _state.value.users.isEmpty()) {
                _state.update { it.copy(isLoading = false, message = error.message) }
            } else {
                _state.update { it.copy(isLoading = false) }
            }
            delay(POLL_MILLIS)
        }
    }

    fun openMember(user: FamilyUserUi) {
        _state.update { it.copy(editing = FamilyMemberEditUi(user = user), peoplePicker = null) }
    }

    fun dismissMember() {
        if (_state.value.editing?.saving == true) return
        _state.update { it.copy(editing = null, peoplePicker = null) }
    }

    fun updateName(name: String) {
        _state.update { s -> s.copy(editing = s.editing?.copy(displayName = name.take(50))) }
    }

    fun updateShowInFamily(show: Boolean) {
        _state.update { s ->
            val edit = s.editing ?: return@update s
            if (edit.user.isMe) s else s.copy(editing = edit.copy(showInFamily = show))
        }
    }

    fun useImmichPhoto() {
        _state.update { s ->
            val edit = s.editing ?: return@update s
            s.copy(
                editing = edit.copy(
                    pickedPersonId = null,
                    clearPhoto = true,
                    previewAvatarUrl = edit.user.immichAvatarUrl,
                    hasCustomPhoto = false,
                ),
            )
        }
    }

    fun openPeoplePicker() {
        _state.update { it.copy(peoplePicker = PeoplePickerUi(isLoading = true)) }
        viewModelScope.launch {
            try {
                val people = repository.namedPeople()
                _state.update { s ->
                    if (s.peoplePicker == null) s
                    else s.copy(peoplePicker = s.peoplePicker.copy(people = people, isLoading = false, error = null))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s ->
                    if (s.peoplePicker == null) s
                    else s.copy(peoplePicker = s.peoplePicker.copy(isLoading = false, error = e.message ?: "Couldn't load people"))
                }
            }
        }
    }

    fun updatePeopleQuery(query: String) {
        _state.update { s -> s.copy(peoplePicker = s.peoplePicker?.copy(query = query)) }
    }

    fun dismissPeoplePicker() {
        _state.update { it.copy(peoplePicker = null) }
    }

    fun selectPerson(person: ImmichPersonItem) {
        _state.update { s ->
            val adding = s.addingPerson
            if (s.editing == null && adding != null) {
                return@update s.copy(
                    peoplePicker = null,
                    addingPerson = adding.copy(
                        pickedPersonId = person.id,
                        photoBytes = null,
                        previewAvatarUrl = person.thumbnailUrl,
                        // An empty name takes the person's name from Immich.
                        name = adding.name.ifBlank { person.name.take(50) },
                    ),
                )
            }
            val edit = s.editing ?: return@update s.copy(peoplePicker = null)
            s.copy(
                peoplePicker = null,
                editing = edit.copy(
                    pickedPersonId = person.id,
                    clearPhoto = false,
                    previewAvatarUrl = person.thumbnailUrl,
                    hasCustomPhoto = true,
                ),
            )
        }
    }

    fun openAddPerson() {
        _state.update { it.copy(addingPerson = AddPersonUi()) }
    }

    fun dismissAddPerson() {
        if (_state.value.addingPerson?.saving == true) return
        _state.update { it.copy(addingPerson = null) }
    }

    fun updateAddPersonName(name: String) {
        _state.update { s -> s.copy(addingPerson = s.addingPerson?.copy(name = name.take(50))) }
    }

    fun updateAddPersonBirthDate(date: BirthDate?) {
        _state.update { s -> s.copy(addingPerson = s.addingPerson?.copy(birthDate = date)) }
    }

    fun updateAddPersonHideYear(hide: Boolean) {
        _state.update { s ->
            val add = s.addingPerson ?: return@update s
            val newBirthDate = if (hide) add.birthDate?.copy(year = null) else add.birthDate
            s.copy(addingPerson = add.copy(hideBirthYear = hide, birthDate = newBirthDate))
        }
    }

    fun updateAddPersonPhoto(bytes: ByteArray?) {
        _state.update { s -> s.copy(addingPerson = s.addingPerson?.copy(photoBytes = bytes)) }
    }

    fun removeAddPersonPhoto() {
        _state.update { s ->
            s.copy(addingPerson = s.addingPerson?.copy(pickedPersonId = null, photoBytes = null, previewAvatarUrl = null))
        }
    }

    fun saveAddPerson() {
        val add = _state.value.addingPerson ?: return
        if (add.name.isBlank() || add.saving) return
        _state.update { it.copy(addingPerson = add.copy(saving = true)) }
        viewModelScope.launch {
            try {
                val effectiveBirthDate = if (add.hideBirthYear) add.birthDate?.copy(year = null) else add.birthDate
                repository.addMember(
                    displayName = add.name.trim(),
                    birthDate = effectiveBirthDate,
                    photoBytes = add.photoBytes,
                    pickedPersonId = add.pickedPersonId,
                )
                _state.update { it.copy(addingPerson = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s ->
                    s.copy(
                        addingPerson = s.addingPerson?.copy(saving = false),
                        message = e.message ?: "Couldn't add member",
                    )
                }
            }
        }
    }

    fun updateMemberBirthDate(date: BirthDate?) {
        _state.update { s ->
            val edit = s.editing ?: return@update s
            val newDate = if (edit.hideBirthYear) date?.copy(year = null) else date
            s.copy(editing = edit.copy(birthDate = newDate, clearBirthDate = date == null))
        }
    }

    fun updateMemberHideYear(hide: Boolean) {
        _state.update { s ->
            val edit = s.editing ?: return@update s
            val newDate = if (hide) edit.birthDate?.copy(year = null) else edit.birthDate
            s.copy(editing = edit.copy(hideBirthYear = hide, birthDate = newDate))
        }
    }

    fun clearMemberBirthDate() {
        _state.update { s ->
            val edit = s.editing ?: return@update s
            s.copy(editing = edit.copy(birthDate = null, clearBirthDate = true))
        }
    }

    fun askDeleteMember() {
        _state.update { s -> s.copy(editing = s.editing?.copy(confirmDelete = true)) }
    }

    fun cancelDeleteMember() {
        _state.update { s -> s.copy(editing = s.editing?.copy(confirmDelete = false)) }
    }

    fun confirmDeleteMember() {
        val edit = _state.value.editing ?: return
        if (edit.saving) return
        _state.update { it.copy(editing = edit.copy(saving = true, confirmDelete = false)) }
        viewModelScope.launch {
            try {
                repository.deleteMember(edit.user.id)
                _state.update { it.copy(editing = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s ->
                    s.copy(
                        editing = s.editing?.copy(saving = false),
                        message = e.message ?: "Couldn't delete member",
                    )
                }
            }
        }
    }

    fun saveMember() {
        val edit = _state.value.editing ?: return
        if (edit.saving) return
        _state.update { it.copy(editing = edit.copy(saving = true)) }
        viewModelScope.launch {
            try {
                val effectiveBirthDate = if (edit.hideBirthYear) edit.birthDate?.copy(year = null) else edit.birthDate
                repository.saveMember(
                    user = edit.user,
                    displayName = edit.displayName,
                    pickedPersonId = edit.pickedPersonId,
                    photoBytes = edit.pickedPhotoBytes,
                    clearPhoto = edit.clearPhoto,
                    showInFamily = edit.showInFamily,
                    birthDate = effectiveBirthDate,
                    clearBirthDate = edit.clearBirthDate,
                )
                _state.update { it.copy(editing = null, peoplePicker = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s ->
                    s.copy(
                        editing = s.editing?.copy(saving = false),
                        message = e.message ?: "Couldn't save changes",
                    )
                }
            }
        }
    }

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }
}
