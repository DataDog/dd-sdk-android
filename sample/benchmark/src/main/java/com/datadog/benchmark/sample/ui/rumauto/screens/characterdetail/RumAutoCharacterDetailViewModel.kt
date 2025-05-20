/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characterdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherQualifier
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherType
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import com.datadog.benchmark.sample.utils.StateMachine
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher

internal data class RumAutoCharacterState(
    val character: Character
)

internal sealed class RumAutoCharacterAction {
    data class UpdateCharacterMessage(val newMessage: String) : RumAutoCharacterAction()
}

internal class RumAutoCharacterDetailViewModelFactory @AssistedInject constructor(
    @Assisted private val character: Character,
    @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default)
    private val defaultDispatcher: CoroutineDispatcher,
): ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RumAutoCharacterDetailsViewModel(
            defaultDispatcher = defaultDispatcher,
            character = character
        ) as T
    }
}

@AssistedFactory
internal interface AssistedRumAutoCharacterDetailViewModelFactory {
    fun create(character: Character): RumAutoCharacterDetailViewModelFactory
}

internal class RumAutoCharacterDetailsViewModel(
    private val defaultDispatcher: CoroutineDispatcher,
    private val character: Character,
) : ViewModel() {
    private val stateMachine = StateMachine.create(
        scope = viewModelScope,
        initialState = RumAutoCharacterState(character),
        dispatcher = defaultDispatcher,
        processAction = ::processAction
    )

    val state = stateMachine.state

    private fun processAction(prev: RumAutoCharacterState, action: RumAutoCharacterAction): RumAutoCharacterState {
        return when (action) {
            is RumAutoCharacterAction.UpdateCharacterMessage -> {
                prev
            }
        }
    }
}
