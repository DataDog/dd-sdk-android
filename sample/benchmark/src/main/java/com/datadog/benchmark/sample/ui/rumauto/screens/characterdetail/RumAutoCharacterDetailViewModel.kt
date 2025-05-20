/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characterdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datadog.benchmark.sample.utils.StateMachine
import kotlinx.coroutines.CoroutineDispatcher

internal data class RumAutoCharacterState(
    val message: String = "Initial Character Message"
)

internal sealed class RumAutoCharacterAction {
    data class UpdateCharacterMessage(val newMessage: String) : RumAutoCharacterAction()
}

internal class RumAutoCharacterDetailsViewModel(
    private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val stateMachine = StateMachine.create(
        scope = viewModelScope,
        initialState = RumAutoCharacterState(),
        dispatcher = defaultDispatcher,
        processAction = ::processAction
    )

    val state = stateMachine.state

    private fun processAction(prev: RumAutoCharacterState, action: RumAutoCharacterAction): RumAutoCharacterState {
        return when (action) {
            is RumAutoCharacterAction.UpdateCharacterMessage -> {
                prev.copy(message = action.newMessage)
            }
        }
    }
}
