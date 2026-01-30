/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

interface StateMachine<in Action, out State> {
    val state: StateFlow<State>
    fun dispatch(action: Action)

    companion object Factory {
        fun <Action, State> create(
            scope: CoroutineScope,
            initialState: State,
            dispatcher: CoroutineDispatcher,
            processAction: suspend (prev: State, action: Action) -> State
        ): StateMachine<Action, State> {
            return StateMachineImpl(scope, initialState, dispatcher, processAction)
        }
    }
}

private class StateMachineImpl<in Action, out State>(
    private val scope: CoroutineScope,
    private val initialState: State,
    private val dispatcher: CoroutineDispatcher,
    private val processAction: suspend (prev: State, action: Action) -> State
) : StateMachine<Action, State> {
    private val actions = Channel<Action>(UNLIMITED)

    override val state: StateFlow<State> = actions
        .receiveAsFlow()
        .scan(initialState, processAction)
        .flowOn(dispatcher)
        .stateIn(scope, SharingStarted.Lazily, initialState)

    override fun dispatch(action: Action) {
        actions.trySend(action)
    }
}
