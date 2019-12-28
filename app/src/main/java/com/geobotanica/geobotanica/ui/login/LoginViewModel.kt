package com.geobotanica.geobotanica.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geobotanica.geobotanica.data.entity.User
import com.geobotanica.geobotanica.data.repo.UserRepo
import com.geobotanica.geobotanica.ui.login.ViewEffect.*
import com.geobotanica.geobotanica.ui.login.ViewEvent.*
import com.geobotanica.geobotanica.util.GbDispatchers
import com.geobotanica.geobotanica.util.Lg
import com.geobotanica.geobotanica.util.SingleLiveEvent
import com.geobotanica.geobotanica.util.mutableLiveData
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class LoginViewModel @Inject constructor (
        private val dispatchers: GbDispatchers,
        private val userRepo: UserRepo
): ViewModel() {
    private val _viewState = mutableLiveData(ViewState())
    val viewState: LiveData<ViewState> = _viewState

    private val _viewEffect = SingleLiveEvent<ViewEffect>()
    val viewEffect: LiveData<ViewEffect> = _viewEffect

    private var selectedUserId: Long = 0L
    private val minLength = 3

    fun onEvent(event: ViewEvent): Unit = when (event) {
        is ViewCreated -> {
            emitViewEffect(InitView)
            viewModelScope.launch {
                val users = userRepo.getAll()
                val lastUser = users.firstOrNull { it.id == event.lastUserId }
                val lastRowIndex = lastUser?.let { users.indexOf(it) } ?: 0

                val nicknames = users.map { it.nickname }
                val isEditTextVisible = nicknames.isEmpty()
                val isNicknameSpinnerVisible = nicknames.isNotEmpty()
                val isFabVisible = nicknames.isNotEmpty()

                updateViewState(
                        spinnerRowIndex = lastRowIndex,
                        nicknames = nicknames,
                        isEditTextVisible = isEditTextVisible,
                        isNicknameSpinnerVisible = isNicknameSpinnerVisible,
                        isFabVisible = isFabVisible
                )
            }; Unit
        }
        is NicknameEditTextChanged -> {
            val editText = event.editText
            val isClearButtonVisible = editText.isNotBlank()
            val isFabVisible = editText.length >= minLength
            updateViewState(
                    nicknameEditText = editText,
                    isClearButtonVisible = isClearButtonVisible,
                    isFabVisible = isFabVisible
            )
        }
        is ClearButtonClicked -> updateViewState(nicknameEditText = "")
        is ItemSelected -> {
            if (newUserSelected(event.rowIndex)) {
                selectedUserId = 0L
                val editTextLength = viewState.value?.nicknameEditText?.length ?: 0
                val isClearButtonVisible = viewState.value?.nicknameEditText?.isNotEmpty() ?: false
                val isFabVisible = editTextLength >= minLength
                updateViewState(
                        spinnerRowIndex = event.rowIndex,
                        isEditTextVisible = true,
                        isClearButtonVisible = isClearButtonVisible,
                        isFabVisible = isFabVisible
                )
            } else {
                viewState.value?.nicknames?.get(event.rowIndex)?.let { nickname ->
                    viewModelScope.launch {
                        selectedUserId = userRepo.getByNickname(nickname).id
                        updateViewState(
                                spinnerRowIndex = event.rowIndex,
                                isEditTextVisible = false,
                                isClearButtonVisible = false,
                                isFabVisible = true
                        )
                    }
                }
            }; Unit
        }
        is FabClicked -> {
                if (selectedUserId != 0L)
                    emitViewEffect(NavigateToNext(selectedUserId))
                else {
                    viewModelScope.launch {
                        val newNickname = viewState.value?.nicknameEditText ?: ""
                        val nicknames = userRepo.getAll().map { it.nickname }
                        if (nicknames.contains(newNickname)) {
                            emitViewEffect(ShowUserExistsSnackbar(newNickname))
                            return@launch
                        }

                        val newUserId = createUser(newNickname)
                        Lg.d("Created new User: $newNickname (id = $newUserId)")
                        emitViewEffect(NavigateToNext(newUserId))
                    }; Unit
                }
        }
    }

    private fun newUserSelected(rowIndex: Int): Boolean = rowIndex == viewState.value?.nicknames?.size

    private suspend fun createUser(nickname: String): Long = withContext(dispatchers.io) {
        userRepo.insert(User(nickname))
    }

    private fun updateViewState(
            nicknames: List<String> = viewState.value?.nicknames ?: emptyList(),
            spinnerRowIndex: Int = viewState.value?.spinnerRowIndex ?: 0,
            isNicknameSpinnerVisible: Boolean = viewState.value?.isNicknameSpinnerVisible ?: false,
            isEditTextVisible: Boolean = viewState.value?.isEditTextVisible ?: false,
            nicknameEditText: String = viewState.value?.nicknameEditText ?: "",
            isClearButtonVisible: Boolean = viewState.value?.isClearButtonVisible ?: false,
            isFabVisible: Boolean = viewState.value?.isFabVisible ?: false
    ) {
        _viewState.value = viewState.value?.copy(
                nicknames = nicknames,
                spinnerRowIndex = spinnerRowIndex,
                isNicknameSpinnerVisible = isNicknameSpinnerVisible,
                isEditTextVisible = isEditTextVisible,
                nicknameEditText = nicknameEditText,
                isClearButtonVisible = isClearButtonVisible,
                isFabVisible = isFabVisible
        )
    }

    private fun emitViewEffect(viewEffect: ViewEffect) {
        _viewEffect.value = viewEffect
    }
}


data class ViewState(
        val nicknames: List<String> = emptyList(),
        val spinnerRowIndex: Int = 0,
        val isNicknameSpinnerVisible: Boolean = false,
        val isEditTextVisible: Boolean = false,
        val nicknameEditText: String = "",
        val isClearButtonVisible: Boolean = false,
        val isFabVisible: Boolean = false
)

sealed class ViewEvent {
    data class ViewCreated(val lastUserId: Long) : ViewEvent()
    data class NicknameEditTextChanged(val editText: String) : ViewEvent()
    object ClearButtonClicked : ViewEvent()
    data class ItemSelected(val rowIndex: Int) : ViewEvent()
    object FabClicked : ViewEvent()
}

sealed class ViewEffect {
    object InitView : ViewEffect()
    data class ShowUserExistsSnackbar(val nickname: String) : ViewEffect()
    data class NavigateToNext(val userId: Long) : ViewEffect()
}