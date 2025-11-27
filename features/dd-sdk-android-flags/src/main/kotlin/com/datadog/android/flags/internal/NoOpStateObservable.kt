/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.StateObservable
import com.datadog.android.flags.model.FlagsClientState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * No-operation implementation of [StateObservable].
 *
 * This implementation always returns [FlagsClientState.Error] state and ignores
 * all listener registration attempts.
 */
internal class NoOpStateObservable : StateObservable {

    private val _stateFlow = MutableStateFlow<FlagsClientState>(FlagsClientState.Error(null))

    override val flow: StateFlow<FlagsClientState> = _stateFlow.asStateFlow()

    override fun getCurrentState(): FlagsClientState = FlagsClientState.Error(null)

    override fun addListener(listener: FlagsStateListener) {
        // No-op - silently ignores listener registration
    }

    override fun removeListener(listener: FlagsStateListener) {
        // No-op - silently ignores listener removal
    }
}
