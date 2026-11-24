/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags._FlagsInternalProxy.SnapshotExposureDecision.ADMITTED
import com.datadog.android.flags._FlagsInternalProxy.SnapshotExposureDecision.DUPLICATE
import com.datadog.android.flags._FlagsInternalProxy.SnapshotExposureDecision.INELIGIBLE
import com.datadog.android.flags._FlagsInternalProxy.SnapshotExposureDecision.UNAVAILABLE
import com.datadog.android.flags.assertj.ExposureEventAssert.Companion.assertThatExposureEvent
import com.datadog.android.flags.internal.evaluation.ExperimentationOptOut
import com.datadog.android.flags.internal.model.ExposureEvent
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.internal.storage.RecordWriter
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagSnapshotFeature
import com.datadog.android.flags.model.UnparsedFlag
import com.datadog.android.flags.model.UnparsedFlagWithFeatures
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.privacy.TrackingConsent
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotExposureTest {
    private val core: InternalSdkCore = mock()
    private val writer: RecordWriter = mock()
    private val evaluations: EvaluationsFeature = mock()
    private val repository: FlagsRepository = mock()

    @StringForgery
    lateinit var flagKey: String

    @Forgery
    lateinit var assignment: PrecomputedFlag

    @Test
    fun `M share record-only admission with native delivery W admitSnapshotExposure()`() {
        // Given
        val client = proxy()
        val flag = assignment.copy(doLog = true, serialId = 0)
        val context = EvaluationContext("")

        // When
        val missing = client.admitSnapshotExposure(flagKey, flag, null, true)
        val first = client.admitSnapshotExposure(flagKey, flag, context, false)
        client.trackFlagExposure(flagKey, flag, context)
        val repeat = client.admitSnapshotExposure(flagKey, flag, context, true)
        val changed = flag.copy(serialId = 9_007_199_254_740_993L)
        val next = client.admitSnapshotExposure(flagKey, changed, context, true)

        // Then
        assertThat(listOf(missing, first, repeat, next)).containsExactly(INELIGIBLE, ADMITTED, DUPLICATE, ADMITTED)
        val events = argumentCaptor<ExposureEvent>()
        verify(writer).write(events.capture())
        assertThat(events.firstValue.serialId).isEqualTo(changed.serialId)
        verifyNoInteractions(evaluations, repository)
    }

    @Test
    fun `M honor disabled native writes W admitSnapshotExposure()`() {
        // Given
        val client = proxy(writeEvents = false)
        val flag = assignment.copy(doLog = true)
        val context = EvaluationContext(flagKey)

        // When
        val first = client.admitSnapshotExposure(flagKey, flag, context, true)
        val repeat = client.admitSnapshotExposure(flagKey, flag, context, true)

        // Then
        assertThat(listOf(first, repeat)).containsExactly(ADMITTED, DUPLICATE)
        verifyNoInteractions(writer)
    }

    @ParameterizedTest
    @EnumSource(value = TrackingConsent::class, names = ["PENDING", "NOT_GRANTED"])
    fun `M leave admission available after consent is granted W admitSnapshotExposure()`(consent: TrackingConsent) {
        // Given
        val client = proxy()
        val context = EvaluationContext(flagKey)
        val flag = assignment.copy(doLog = true)
        whenever(core.trackingConsent).thenReturn(consent, TrackingConsent.GRANTED)

        // When
        val before = client.admitSnapshotExposure(flagKey, flag, context, true)
        val after = client.admitSnapshotExposure(flagKey, flag, context, false)

        // Then
        assertThat(listOf(before, after)).containsExactly(UNAVAILABLE, ADMITTED)
        verifyNoInteractions(writer)
    }

    @ParameterizedTest
    @ValueSource(strings = ["allocation", "variant", "unlogged"])
    fun `M skip an ineligible assignment W admitSnapshotExposure()`(condition: String) {
        // Given
        val client = proxy()
        val flag = assignment.copy(
            allocationKey = if (condition == "allocation") " " else assignment.allocationKey,
            variationKey = if (condition == "variant") "\t" else assignment.variationKey,
            doLog = condition != "unlogged"
        )

        // When
        val decision = client.admitSnapshotExposure(flagKey, flag, EvaluationContext(flagKey), true)

        // Then
        assertThat(decision).isEqualTo(INELIGIBLE)
        verifyNoInteractions(writer)
    }

    @Test
    fun `M require a verifiable context under experimentation opt-out W admitSnapshotExposure()`() {
        // Given
        val policy = ExperimentationOptOut(ExperimentationOptOutMode.IMMEDIATE)
        val client = proxy(policy = policy)
        val context = EvaluationContext(flagKey, mapOf(ExperimentationOptOut.ATTRIBUTE to "2026-01-01T00:00:00Z"))
        val flag = assignment.copy(doLog = true)
        policy.prepare(context)

        // When
        val marked = client.admitSnapshotExposure(flagKey, flag, context, true)
        val stale = client.admitSnapshotExposure(flagKey, flag, EvaluationContext(flagKey), true)

        // Then
        assertThat(listOf(marked, stale)).containsExactly(UNAVAILABLE, UNAVAILABLE)
        verifyNoInteractions(writer)
    }

    @Test
    fun `M return unavailable for a foreign client W admitSnapshotExposure()`() {
        // Given
        val client = _FlagsInternalProxy(mock())

        // When
        val decision = client.admitSnapshotExposure(flagKey, assignment, EvaluationContext(flagKey), true)

        // Then
        assertThat(decision).isEqualTo(UNAVAILABLE)
    }

    @Test
    fun `M route the replayed features W admitSnapshotExposure() and trackFlagEvaluationEvent()`() {
        // Given
        val client = proxy()
        val native = assignment.copy(
            doLog = true,
            features = listOf(
                FlagSnapshotFeature("hook", false, listOf("HOOK")),
                FlagSnapshotFeature("exposure", "2026", listOf("EXPOSURE")),
                FlagSnapshotFeature("count", 0.0, listOf("EVALUATION"))
            )
        )
        val flag = object : UnparsedFlag by native, UnparsedFlagWithFeatures {
            override val features = (native as UnparsedFlagWithFeatures).features
        }
        val context = EvaluationContext(flagKey, mapOf("dd_assignment_token" to flagKey))

        // When
        val admitted = client.admitSnapshotExposure(flagKey, flag, context, true)
        client.trackFlagEvaluationEvent(flagKey, flag, context, null)

        // Then
        assertThat(admitted).isEqualTo(ADMITTED)
        val events = argumentCaptor<ExposureEvent>()
        verify(writer).write(events.capture())
        assertThatExposureEvent(events.firstValue).hasFeatures(mapOf("exposure" to "2026"))
        assertThat(events.firstValue.subject.attributes.additionalProperties).doesNotContainKey("dd_assignment_token")
        verify(evaluations).processEvaluation(
            eq(flagKey), eq(EvaluationContext(flagKey)), eq(flag.variationKey), eq(flag.allocationKey),
            isNull(), isNull(), eq(false), eq(flagKey), anyOrNull(), eq(mapOf("count" to 0.0))
        )
    }

    private fun proxy(
        writeEvents: Boolean = true,
        policy: ExperimentationOptOut = ExperimentationOptOut()
    ): _FlagsInternalProxy {
        whenever(core.trackingConsent).thenReturn(TrackingConsent.GRANTED)
        return _FlagsInternalProxy(
            DatadogFlagsClient(
                core, mock(), repository, FlagsConfiguration.Builder().trackEvaluations(true).build(),
                mock(), ExposureEventsProcessor(writer, mock(), writeEvents), evaluations, mock(), false, policy
            )
        )
    }
}
