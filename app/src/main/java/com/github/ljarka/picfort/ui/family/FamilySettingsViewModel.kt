package com.github.ljarka.picfort.ui.family

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun saveMember() {
        val edit = _state.value.editing ?: return
        if (edit.saving) return
        _state.update { it.copy(editing = edit.copy(saving = true)) }
        viewModelScope.launch {
            try {
                repository.saveMember(
                    user = edit.user,
                    displayName = edit.displayName,
                    pickedPersonId = edit.pickedPersonId,
                    clearPhoto = edit.clearPhoto,
                    showInFamily = edit.showInFamily,
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
