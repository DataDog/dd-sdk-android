/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.internal.FlagsStateManager
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsReader
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.internal.utils.DDCoreStateHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ExperimentationCallbackTest {
    @Mock lateinit var mockCore: FeatureSdkCore

    @Mock lateinit var mockExecutor: ExecutorService

    @Mock lateinit var mockLogger: InternalLogger

    @Mock lateinit var mockRepository: FlagsRepository

    @Mock lateinit var mockReader: PrecomputedAssignmentsReader

    @Mock lateinit var mockMapper: PrecomputeMapper

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class, names = ["IMMEDIATE", "FUTURE_EXPERIMENTS_ONLY"])
    fun `M isolate callback exception W updateEvaluationsForContext() {malformed cutoff}`(
        mode: ExperimentationOptOutMode
    ) {
        // Given
        val states = stateManager()
        val testedManager = manager(mode, states)
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() = Unit
            override fun onFailure(error: Throwable) { error("customer callback failed") }
        }

        // When / Then
        assertDoesNotThrow { testedManager.updateEvaluationsForContext(invalidContext(), callback) }
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class, names = ["IMMEDIATE", "FUTURE_EXPERIMENTS_ONLY"])
    fun `M publish error before failure callback W updateEvaluationsForContext() {listener reentry}`(
        mode: ExperimentationOptOutMode
    ) {
        // Given
        val states = stateManager()
        val testedManager = manager(mode, states)
        var callbackState: FlagsClientState? = null
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() = Unit
            override fun onFailure(error: Throwable) { callbackState = states.getCurrentState() }
        }
        states.addListener(object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                if (newState == FlagsClientState.Reconciling) {
                    testedManager.updateEvaluationsForContext(invalidContext(), callback)
                }
            }
        })

        // When
        testedManager.updateEvaluationsForContext(EvaluationContext("subject"))

        // Then
        assertThat(states.getCurrentState()).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat(callbackState).isInstanceOf(FlagsClientState.Error::class.java)
    }

    private fun invalidContext() = EvaluationContext("subject", mapOf("dd_experimentation_opt_out_at" to "invalid"))
    private fun stateManager() = FlagsStateManager(
        DDCoreStateHolder.create<FlagsClientState, FlagsStateListener>(FlagsClientState.NotReady) { onStateChanged(it) }
    )
    private fun manager(mode: ExperimentationOptOutMode, states: FlagsStateManager) = EvaluationsManager(
        mockCore, mockExecutor, mockLogger, mockRepository, mockReader, mockMapper, states,
        null, { _, _ -> {} }, ExperimentationOptOut(mode)
    )
}
