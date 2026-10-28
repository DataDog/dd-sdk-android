/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.model.EvaluationContext
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EvaluationCallbackDispatchTest {
    @Mock lateinit var mockCore: FeatureSdkCore

    @Mock lateinit var mockLogger: InternalLogger

    @Mock lateinit var mockNetworkWorker: ExecutorService

    @Mock lateinit var mockCallbackWorker: ExecutorService

    @StringForgery lateinit var fakeSubject: String

    private lateinit var testedClient: FlagsClient
    private lateinit var testedFeature: FlagsFeature
    private val pending = ArrayDeque<Runnable>()

    @BeforeEach
    fun setUp() {
        whenever(mockCore.timeProvider).thenReturn(mock())
        whenever(mockCore.internalLogger).thenReturn(mockLogger)
        whenever(mockCore.createOkHttpCallFactory()).thenReturn(mock())
        whenever(mockCore.createSingleThreadExecutorService(any())).thenAnswer {
            if (it.getArgument<String>(0) == FlagsClient.FLAGS_CALLBACK_EXECUTOR_NAME) {
                mockCallbackWorker
            } else {
                mockNetworkWorker
            }
        }
        whenever(mockCallbackWorker.execute(any())).thenAnswer {
            if (pending.size < 1) pending.addLast(it.getArgument(0))
            null
        }
        val configuration = FlagsConfiguration.Builder().trackEvaluations(false).build()
        testedFeature = FlagsFeature(mockCore, configuration)
        testedClient = FlagsClient.createInternal(
            configuration,
            mockCore,
            testedFeature,
            null,
            fakeSubject,
            ExperimentationOptOutMode.IMMEDIATE,
            false
        )
        testedFeature.getOrRegisterNewClient(fakeSubject) { testedClient }
    }

    @Test
    fun `M shut down callback worker W setEvaluationContext() {delivery pending}`() {
        // Given
        val callback = callback {}

        // When
        testedClient.setEvaluationContext(invalidContext(), callback)

        // Then
        verify(mockCallbackWorker).shutdown()
        assertThat(pending).hasSize(1)
    }

    @Test
    fun `M retain every callback W setEvaluationContext() {worker queue drops extra tasks}`() {
        // Given
        var delivered = 0
        val callback = callback { delivered++ }

        // When
        repeat(3) { testedClient.setEvaluationContext(invalidContext(), callback) }
        pending.removeFirst().run()

        // Then
        assertThat(delivered).isEqualTo(3)
        verify(mockCallbackWorker).execute(any())
    }

    @Test
    fun `M create a new worker W setEvaluationContext() {previous delivery drained}`() {
        // Given
        val callback = callback {}
        testedClient.setEvaluationContext(invalidContext(), callback)
        pending.removeFirst().run()

        // When
        testedClient.setEvaluationContext(invalidContext(), callback)

        // Then
        verify(mockCore, times(2)).createSingleThreadExecutorService(FlagsClient.FLAGS_CALLBACK_EXECUTOR_NAME)
        verify(mockCallbackWorker, times(2)).shutdown()
    }

    @Test
    fun `M deliver accepted callbacks W onStop() {delivery pending}`() {
        // Given
        var delivered = 0
        val callback = callback { delivered++ }
        repeat(3) { testedClient.setEvaluationContext(invalidContext(), callback) }

        // When
        testedFeature.onStop()
        pending.removeFirst().run()

        // Then
        assertThat(delivered).isEqualTo(3)
        assertThat(testedFeature.getClient(fakeSubject)).isNull()
        verify(mockCallbackWorker).shutdown()
    }

    private fun invalidContext() = EvaluationContext(
        fakeSubject,
        mapOf("dd_experimentation_opt_out_at" to "invalid")
    )

    private fun callback(onFailure: () -> Unit) = object : EvaluationContextCallback {
        override fun onSuccess() = Unit
        override fun onFailure(error: Throwable) = onFailure()
    }
}
