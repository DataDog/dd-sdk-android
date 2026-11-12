/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.openfeature.internal.exposure.DatadogProviderMetadata
import com.datadog.android.flags.openfeature.internal.exposure.ExposureDecisions
import com.datadog.tools.unit.forge.BaseConfigurator
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.EvaluationMetadata
import dev.openfeature.kotlin.sdk.FlagEvaluationDetails
import dev.openfeature.kotlin.sdk.FlagValueType
import dev.openfeature.kotlin.sdk.HookContext
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.ProviderMetadata
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import com.datadog.android.flags.model.EvaluationContext as DatadogEvaluationContext

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class ExposureHookTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockDefaultLogger: InternalLogger

    @StringForgery
    lateinit var fakeFlagKey: String

    @StringForgery
    lateinit var fakeTargetingKey: String

    private val evaluations = mutableListOf<ExposureEvaluation>()
    private var decisionId = 0
    private val decisions = ExposureDecisions()
    private lateinit var providerMetadata: DatadogProviderMetadata
    private lateinit var fakeContext: LiveContext
    private lateinit var testedHook: ExposureHook

    @BeforeEach
    fun `set up`() {
        fakeContext = LiveContext(fakeTargetingKey, mutableMapOf("plan" to Value.String("pro")))
        providerMetadata = DatadogProviderMetadata("Datadog", decisions, mockInternalLogger)
        testedHook = ExposureHook({ evaluations.add(it) }, { mockDefaultLogger })
    }

    @Test
    fun `M report a new exposure W finallyAfter() {exposure not recorded}`() {
        // Given
        val details = exposureDetails(isRecorded = false, features = mapOf("holdout.key" to "holdout-1"))

        // When
        testedHook.finallyAfter(hookContext(), details, emptyMap())

        // Then
        val evaluation = evaluations.single()
        assertThat(evaluation.isExposure()).isTrue()
        assertThat(evaluation.shouldSend()).isTrue()
        assertThat(evaluation.isCacheHit()).isFalse()
        assertThat(evaluation.flagKey).isEqualTo(fakeFlagKey)
        assertThat(evaluation.variant).isEqualTo("on")
        assertThat(evaluation.allocationKey).isEqualTo("allocation")
        assertThat(evaluation.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(evaluation.features).containsExactlyEntriesOf(mapOf("holdout.key" to "holdout-1"))
    }

    @Test
    fun `M report a repeat W finallyAfter() {exposure already recorded}`() {
        // Given
        val details = exposureDetails(isRecorded = true)

        // When
        testedHook.finallyAfter(hookContext(), details, emptyMap())

        // Then
        val evaluation = evaluations.single()
        assertThat(evaluation.isExposure()).isTrue()
        assertThat(evaluation.isCacheHit()).isTrue()
        assertThat(evaluation.shouldSend()).isFalse()
    }

    @Test
    fun `M use the final verdict W finallyAfter() {provisional verdict outdated}`() {
        // Given
        val details = exposureDetails(isRecorded = true, provisionalIsRecorded = false)

        // When
        testedHook.finallyAfter(hookContext(), details, emptyMap())

        // Then
        assertThat(evaluations.single().isCacheHit()).isTrue()
    }

    @Test
    fun `M report no exposure W finallyAfter() {failed evaluation}`() {
        // Given
        var recorded = false
        val metadata = exposureMetadata(provisionalIsRecorded = false)
        decisions.register(metadata, emptyMap(), null) {
            recorded = true
            false
        }
        val details =
            FlagEvaluationDetails<Any>(fakeFlagKey, true, "on", errorCode = ErrorCode.GENERAL, metadata = metadata)

        // When
        testedHook.finallyAfter(hookContext(), details, emptyMap())

        // Then
        val evaluation = evaluations.single()
        assertThat(evaluation.isExposure()).isFalse()
        assertThat(evaluation.shouldSend()).isFalse()
        assertThat(evaluation.isCacheHit()).isFalse()
        assertThat(recorded).isFalse()
    }

    @Test
    fun `M report no exposure W finallyAfter() {allocation does not log}`() {
        // Given
        val details = FlagEvaluationDetails<Any>(
            fakeFlagKey,
            true,
            "on",
            metadata = EvaluationMetadata.builder().putString("allocationKey", "allocation").build()
        )

        // When
        testedHook.finallyAfter(hookContext(), details, emptyMap())

        // Then
        assertThat(evaluations.single().isExposure()).isFalse()
        assertThat(evaluations.single().features).isEmpty()
    }

    @Test
    fun `M report no exposure W finallyAfter() {exposure keys in metadata without a decision}`() {
        // Given
        val details = FlagEvaluationDetails<Any>(fakeFlagKey, true, "on", metadata = exposureMetadata(false))

        // When
        testedHook.finallyAfter(hookContext(), details, emptyMap())

        // Then
        assertThat(evaluations.single().isExposure()).isFalse()
        assertThat(evaluations.single().shouldSend()).isFalse()
    }

    @Test
    fun `M identify the assignment subject W finallyAfter() {OpenFeature context changed}`(
        @StringForgery fakeAssignmentKey: String
    ) {
        // Given
        val metadata = exposureMetadata(false)
        val assignmentContext = DatadogEvaluationContext(fakeAssignmentKey, mapOf("plan" to "pro"))
        decisions.register(metadata, emptyMap(), assignmentContext) { false }
        val details = FlagEvaluationDetails<Any>(fakeFlagKey, true, "on", metadata = metadata)

        // When
        testedHook.finallyAfter(hookContext(), details, emptyMap())

        // Then
        val evaluation = evaluations.single()
        assertThat(evaluation.targetingKey).isEqualTo(fakeAssignmentKey)
        val context = checkNotNull(evaluation.evaluationContext)
        assertThat(context.getTargetingKey()).isEqualTo(fakeAssignmentKey)
        assertThat(context.getValue("plan")).isEqualTo(Value.String("pro"))
    }

    @Test
    fun `M report a non-exposure W finallyAfter() {stale evaluation}`() {
        // Given
        val metadata = exposureMetadata(false)
        decisions.register(metadata, emptyMap(), null) { null }
        val details = FlagEvaluationDetails<Any>(fakeFlagKey, true, "on", metadata = metadata)

        // When
        testedHook.finallyAfter(hookContext(), details, emptyMap())

        // Then
        val evaluation = evaluations.single()
        assertThat(evaluation.isExposure()).isFalse()
        assertThat(evaluation.shouldSend()).isFalse()
        assertThat(evaluation.isCacheHit()).isFalse()
    }

    @Test
    fun `M give each callback its own features W finallyAfter() {two hooks, first changes its map}`() {
        // Given
        val details = exposureDetails(isRecorded = false, features = mapOf("holdout.key" to "holdout-1"))
        val firstFeatures = mutableListOf<Map<String, Any>>()
        val changingHook = ExposureHook(
            { evaluation ->
                firstFeatures.add(evaluation.features)
                @Suppress("UNCHECKED_CAST", "DontDowncastCollectionTypes")
                (evaluation.features as MutableMap<String, Any>).clear()
            },
            { mockDefaultLogger }
        )

        // When
        changingHook.finallyAfter(hookContext(), details, emptyMap())
        testedHook.finallyAfter(hookContext(), details, emptyMap())

        // Then
        assertThat(firstFeatures.single()).isEmpty()
        assertThat(evaluations.single().features).containsExactlyEntriesOf(mapOf("holdout.key" to "holdout-1"))
        assertThat(evaluations.single().features).isNotSameAs(firstFeatures.single())
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M pass a copy of the context W finallyAfter()`() {
        // Given
        val details = exposureDetails(isRecorded = false)
        testedHook.finallyAfter(hookContext(), details, emptyMap())

        // When
        fakeContext.attributes["plan"] = Value.String("free")

        // Then
        assertThat(checkNotNull(evaluations.single().evaluationContext).getValue("plan"))
            .isEqualTo(Value.String("pro"))
    }

    @Test
    fun `M ignore the evaluation W finallyAfter() {not a Datadog provider}`() {
        // Given
        val otherProvider = object : ProviderMetadata {
            override val name: String = "other"
        }

        // When
        testedHook.finallyAfter(hookContext(otherProvider), exposureDetails(isRecorded = false), emptyMap())
        testedHook.finallyAfter(hookContext(otherProvider), exposureDetails(isRecorded = false), emptyMap())

        // Then
        assertThat(evaluations).isEmpty()
        val messageCaptor = argumentCaptor<() -> String>()
        verify(mockDefaultLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            messageCaptor.capture(),
            isNull(),
            eq(false),
            isNull()
        )
        assertThat(messageCaptor.firstValue()).isEqualTo(
            "ExposureHook only reports evaluations of a DatadogFlagsProvider set directly as the OpenFeature " +
                "provider. Other evaluations are ignored."
        )
    }

    @Test
    fun `M log the first failure as a warning and later ones at debug level W finallyAfter() {callback throws}`() {
        // Given
        val failure = IllegalStateException("callback failed")
        testedHook = ExposureHook({ throw failure }, { mockDefaultLogger })

        // When
        assertDoesNotThrow {
            testedHook.finallyAfter(hookContext(), exposureDetails(isRecorded = false), emptyMap())
            testedHook.finallyAfter(hookContext(), exposureDetails(isRecorded = false), emptyMap())
        }

        // Then
        val messageCaptor = argumentCaptor<() -> String>()
        inOrder(mockInternalLogger) {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                messageCaptor.capture(),
                eq(failure),
                eq(false),
                isNull()
            )
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.DEBUG),
                eq(InternalLogger.Target.USER),
                messageCaptor.capture(),
                eq(failure),
                eq(false),
                isNull()
            )
        }
        assertThat(messageCaptor.allValues.map { it() })
            .containsOnly("Exposure callback failed; later failures are logged at debug level.")
        verifyNoInteractions(mockDefaultLogger)
    }

    @Test
    fun `M not throw W finallyAfter() {callback and logger throw}`() {
        // Given
        whenever(
            mockInternalLogger.log(any(), isA<InternalLogger.Target>(), any(), anyOrNull(), any(), anyOrNull())
        ) doThrow IllegalStateException("logger failed")
        testedHook = ExposureHook({ throw IllegalStateException("callback failed") }, { mockDefaultLogger })

        // When / Then
        assertDoesNotThrow {
            testedHook.finallyAfter(hookContext(), exposureDetails(isRecorded = false), emptyMap())
        }
    }

    @Test
    fun `M not log W finallyAfter() {callback succeeds}`() {
        // When
        testedHook.finallyAfter(hookContext(), exposureDetails(isRecorded = false), emptyMap())

        // Then
        verifyNoInteractions(mockInternalLogger, mockDefaultLogger)
    }

    private fun exposureDetails(
        isRecorded: Boolean,
        provisionalIsRecorded: Boolean = isRecorded,
        features: Map<String, Any> = emptyMap()
    ): FlagEvaluationDetails<Any> {
        val metadata = exposureMetadata(provisionalIsRecorded)
        decisions.register(metadata, features, null) { isRecorded }
        return FlagEvaluationDetails(fakeFlagKey, true, "on", metadata = metadata)
    }

    private fun exposureMetadata(provisionalIsRecorded: Boolean) = EvaluationMetadata.builder()
        .putString("allocationKey", "allocation")
        .putBoolean("__dd_do_log", true)
        .putBoolean("__dd_exposure_cache_hit", provisionalIsRecorded)
        .putInt("__dd_exposure_decision", ++decisionId)
        .build()

    private fun hookContext(metadata: ProviderMetadata = providerMetadata) =
        HookContext<Any>(fakeFlagKey, FlagValueType.BOOLEAN, false, fakeContext, null, metadata)

    class LiveContext(
        private val targetingKey: String,
        val attributes: MutableMap<String, Value>
    ) : EvaluationContext {
        override fun getTargetingKey(): String = targetingKey
        override fun withTargetingKey(targetingKey: String): EvaluationContext = LiveContext(targetingKey, attributes)
        override fun keySet(): Set<String> = attributes.keys
        override fun getValue(key: String): Value? = attributes[key]
        override fun asMap(): Map<String, Value> = attributes
        override fun asObjectMap(): Map<String, Any?> = ImmutableContext(targetingKey, attributes).asObjectMap()
        override fun hashCode(): Int = attributes.hashCode()
        override fun equals(other: Any?): Boolean = other is LiveContext && other.attributes == attributes
    }
}
