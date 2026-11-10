/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.exposure

import com.datadog.tools.unit.forge.BaseConfigurator
import dev.openfeature.kotlin.sdk.EvaluationMetadata
import dev.openfeature.kotlin.sdk.FlagEvaluationDetails
import dev.openfeature.kotlin.sdk.Reason
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(BaseConfigurator::class)
internal class ExposureDecisionsTest {

    @StringForgery
    lateinit var fakeFlagKey: String

    private var recordCount = 0
    private var decisionId = 0
    private lateinit var testedDecisions: ExposureDecisions

    @BeforeEach
    fun `set up`() {
        testedDecisions = ExposureDecisions()
    }

    @Test
    fun `M record once W complete() twice`(@BoolForgery fakeIsRecorded: Boolean) {
        // Given
        val metadata = newMetadata()
        testedDecisions.register(metadata, emptyMap(), null, record(fakeIsRecorded))

        // When
        val first = testedDecisions.complete(details(metadata))
        val second = testedDecisions.complete(details(metadata))

        // Then
        assertThat(first).isEqualTo(fakeIsRecorded)
        assertThat(second).isEqualTo(fakeIsRecorded)
        assertThat(recordCount).isEqualTo(1)
    }

    @Test
    fun `M record nothing W complete() {failed evaluation}`() {
        // Given
        val metadata = newMetadata()
        testedDecisions.register(metadata, emptyMap(), null, record(false))

        // When
        val withErrorCode = testedDecisions.complete(details(metadata).copy(errorCode = ErrorCode.GENERAL))
        val withErrorReason = testedDecisions.complete(details(metadata).copy(reason = Reason.ERROR.name))

        // Then
        assertThat(withErrorCode).isNull()
        assertThat(withErrorReason).isNull()
        assertThat(recordCount).isZero()
    }

    @Test
    fun `M keep apart W complete() {metadata of another evaluation}`() {
        // Given
        testedDecisions.register(newMetadata(), emptyMap(), null, record(false))

        // When
        val result = testedDecisions.complete(details(newMetadata()))

        // Then
        assertThat(result).isNull()
        assertThat(recordCount).isZero()
    }

    @Test
    fun `M share the decision W complete() {other thread}`() {
        // Given
        val metadata = newMetadata()
        testedDecisions.register(metadata, emptyMap(), null, record(true))
        val executor = Executors.newSingleThreadExecutor()

        // When
        val result = AtomicReference<Boolean?>(null)
        executor.submit { result.set(testedDecisions.complete(details(metadata))) }.get(5, TimeUnit.SECONDS)
        executor.shutdown()
        val repeated = testedDecisions.complete(details(metadata))

        // Then
        assertThat(result.get()).isTrue()
        assertThat(repeated).isTrue()
        assertThat(recordCount).isEqualTo(1)
    }

    @Test
    fun `M survive 17 nested evaluations W register() + complete()`() {
        // Given
        val outer = newMetadata()
        testedDecisions.register(outer, mapOf("holdout.key" to "holdout-1"), null, record(false))

        // When
        val nested = List(17) { newMetadata() }
        nested.forEach { testedDecisions.register(it, emptyMap(), null, record(false)) }
        val result = testedDecisions.complete(details(outer))

        // Then
        assertThat(result).isFalse()
        assertThat(checkNotNull(testedDecisions.find(details(outer))).hookFeatures)
            .containsEntry("holdout.key", "holdout-1")
        assertThat(nested.map { testedDecisions.find(details(it)) }).doesNotContainNull()
    }

    @Test
    fun `M release the decision W register() {evaluation result dropped}`() {
        // Given
        val released = registerAndDrop()

        // When
        var attempts = 0
        while (released.get() != null && attempts < MAX_GC_ATTEMPTS) {
            System.gc()
            attempts++
        }

        // Then
        assertThat(released.get()).isNull()
    }

    private fun registerAndDrop(): WeakReference<EvaluationMetadata> {
        val metadata = newMetadata()
        testedDecisions.register(metadata, mapOf("holdout.key" to "holdout-1"), null, record(false))
        return WeakReference(metadata)
    }

    @Test
    fun `M keep hook features W register() + find() {no exposure}`() {
        // Given
        val metadata = newMetadata()
        val features = mapOf("holdout.key" to "holdout-1")

        // When
        testedDecisions.register(metadata, features, null, null)

        // Then
        assertThat(checkNotNull(testedDecisions.find(details(metadata))).hookFeatures).isEqualTo(features)
        assertThat(testedDecisions.complete(details(metadata))).isNull()
    }

    @Test
    fun `M keep nothing W register() {no features and no exposure}`() {
        // Given
        val metadata = newMetadata()

        // When
        testedDecisions.register(metadata, emptyMap(), null, null)

        // Then
        assertThat(testedDecisions.find(details(metadata))).isNull()
    }

    private fun record(isRecorded: Boolean): () -> Boolean = {
        recordCount++
        isRecorded
    }

    private fun newMetadata(): EvaluationMetadata = EvaluationMetadata.builder()
        .putBoolean("__dd_do_log", true)
        .putInt("__dd_exposure_decision", ++decisionId)
        .build()

    private fun details(metadata: EvaluationMetadata) =
        FlagEvaluationDetails(fakeFlagKey, true, variant = "on", metadata = metadata)

    private companion object {
        const val MAX_GC_ATTEMPTS = 50
    }
}
