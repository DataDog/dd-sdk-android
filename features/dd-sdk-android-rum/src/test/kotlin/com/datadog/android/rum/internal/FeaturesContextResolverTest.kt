/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class FeaturesContextResolverTest {

    lateinit var testedFeaturesContextResolver: FeaturesContextResolver

    lateinit var fakeViewId: String

    @LongForgery(min = 0)
    var fakeRecordsCount: Long = 0

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeViewId = forge.getForgery<UUID>().toString()
        testedFeaturesContextResolver = FeaturesContextResolver()
    }

    @Test
    fun `M return true W resolveHasReplay {sessionReplayContext isRecording}`(forge: Forge) {
        // Given
        val fakeFeaturesContext = mapOf(
            Feature.SESSION_REPLAY_FEATURE_NAME to mapOf(
                fakeViewId to
                    mapOf(FeaturesContextResolver.HAS_REPLAY_KEY to true)
            )
        )
        val fakeDatadogContext: DatadogContext = forge.getForgery<DatadogContext>()
            .copy(featuresContext = fakeFeaturesContext)

        // When
        val hasReplay = testedFeaturesContextResolver.resolveViewHasReplay(
            fakeDatadogContext,
            fakeViewId
        )

        // Then
        assertThat(hasReplay).isTrue
    }

    @Test
    fun `M return false W resolveHasReplay {sessionReplayContext isNotRecording}`(forge: Forge) {
        // Given
        val fakeFeaturesContext = mapOf(
            Feature.SESSION_REPLAY_FEATURE_NAME to mapOf(
                fakeViewId to
                    mapOf(FeaturesContextResolver.HAS_REPLAY_KEY to false)
            )
        )
        val fakeDatadogContext: DatadogContext = forge.getForgery<DatadogContext>()
            .copy(featuresContext = fakeFeaturesContext)

        // When
        val hasReplay = testedFeaturesContextResolver.resolveViewHasReplay(
            fakeDatadogContext,
            fakeViewId
        )

        // Then
        assertThat(hasReplay).isFalse
    }

    @Test
    fun `M return false W resolveHasReplay {no valid type for hasReplay}`(forge: Forge) {
        // Given
        val fakeFeaturesContext = mapOf(
            Feature.SESSION_REPLAY_FEATURE_NAME to mapOf(
                fakeViewId to
                    mapOf(FeaturesContextResolver.HAS_REPLAY_KEY to forge.aString())
            )
        )
        val fakeDatadogContext: DatadogContext = forge.getForgery<DatadogContext>()
            .copy(featuresContext = fakeFeaturesContext)

        // When
        val hasReplay = testedFeaturesContextResolver.resolveViewHasReplay(
            fakeDatadogContext,
            fakeViewId
        )

        // Then
        assertThat(hasReplay).isFalse
    }

    @Test
    fun `M return false W resolveHasReplay {no entry for hasReplay}`(forge: Forge) {
        // Given
        val fakeFeaturesContext = mapOf(
            Feature.SESSION_REPLAY_FEATURE_NAME to mapOf(
                fakeViewId to
                    mapOf(FeaturesContextResolver.HAS_REPLAY_KEY to forge.aString())
            )
        )
        val fakeDatadogContext: DatadogContext = forge.getForgery<DatadogContext>()
            .copy(featuresContext = fakeFeaturesContext)

        // When
        val hasReplay = testedFeaturesContextResolver.resolveViewHasReplay(
            fakeDatadogContext,
            fakeViewId
        )

        // Then
        assertThat(hasReplay).isFalse
    }

    @Test
    fun `M return false W resolveHasReplay {no entry for SR feature context}`(forge: Forge) {
        // Given
        val fakeDatadogContext: DatadogContext = forge.getForgery<DatadogContext>()

        // When
        val hasReplay = testedFeaturesContextResolver.resolveViewHasReplay(
            fakeDatadogContext,
            fakeViewId
        )

        // Then
        assertThat(hasReplay).isFalse
    }

    @Test
    fun `M return false W resolveHasReplay {no entry for viewId}`(forge: Forge) {
        // Given
        val fakeFeaturesContext = mapOf(
            Feature.SESSION_REPLAY_FEATURE_NAME to
                forge.aMap { forge.aString() to forge.aString() }
        )
        val fakeDatadogContext: DatadogContext = forge.getForgery<DatadogContext>()
            .copy(featuresContext = fakeFeaturesContext)

        // When
        val hasReplay = testedFeaturesContextResolver.resolveViewHasReplay(
            fakeDatadogContext,
            fakeViewId
        )

        // Then
        assertThat(hasReplay).isFalse
    }

    @Test
    fun `M return valid records count W resolveViewRecordsCount`(forge: Forge) {
        // Given
        val fakeFeaturesContext = mapOf(
            Feature.SESSION_REPLAY_FEATURE_NAME to mapOf(
                fakeViewId to
                    mapOf(FeaturesContextResolver.VIEW_RECORDS_COUNT_KEY to fakeRecordsCount)
            )
        )
        val fakeDatadogContext: DatadogContext = forge.getForgery<DatadogContext>()
            .copy(featuresContext = fakeFeaturesContext)

        // When
        val viewRecordsCount = testedFeaturesContextResolver.resolveViewRecordsCount(
            fakeDatadogContext,
            fakeViewId
        )

        // Then
        assertThat(viewRecordsCount).isEqualTo(fakeRecordsCount)
    }

    @Test
    fun `M return 0 W resolveViewRecordsCount {no valid type for viewRecordsCount}`(forge: Forge) {
        // Given
        val fakeFeaturesContext = mapOf(
            Feature.SESSION_REPLAY_FEATURE_NAME to mapOf(
                fakeViewId to
                    mapOf(FeaturesContextResolver.VIEW_RECORDS_COUNT_KEY to forge.aString())
            )
        )
        val fakeDatadogContext: DatadogContext = forge.getForgery<DatadogContext>()
            .copy(featuresContext = fakeFeaturesContext)

        // When
        val viewRecordsCount = testedFeaturesContextResolver.resolveViewRecordsCount(
            fakeDatadogContext,
            fakeViewId
        )

        // Then
        assertThat(viewRecordsCount).isEqualTo(0L)
    }

    @Test
    fun `M return 0 W resolveViewRecordsCount {no entry for viewRecordsCount}`(forge: Forge) {
        // Given
        val fakeFeaturesContext = mapOf(
            Feature.SESSION_REPLAY_FEATURE_NAME to
                mapOf(fakeViewId to forge.aMap { forge.aString() to forge.aString() })
        )
        val fakeDatadogContext: DatadogContext = forge.getForgery<DatadogContext>()
            .copy(featuresContext = fakeFeaturesContext)

        // When
        val viewRecordsCount = testedFeaturesContextResolver.resolveViewRecordsCount(
            fakeDatadogContext,
            fakeViewId
        )

        // Then
        assertThat(viewRecordsCount).isEqualTo(0L)
    }

    @Test
    fun `M return 0 W resolveViewRecordsCount {no entry for viewId}`(forge: Forge) {
        // Given
        val fakeFeaturesContext = mapOf(
            Feature.SESSION_REPLAY_FEATURE_NAME to
                forge.aMap { forge.aString() to forge.aString() }
        )
        val fakeDatadogContext: DatadogContext = forge.getForgery<DatadogContext>()
            .copy(featuresContext = fakeFeaturesContext)

        // When
        val viewRecordsCount = testedFeaturesContextResolver.resolveViewRecordsCount(
            fakeDatadogContext,
            fakeViewId
        )

        // Then
        assertThat(viewRecordsCount).isEqualTo(0L)
    }

    @Test
    fun `M return 0 W resolveViewRecordsCount {no entry for SR feature context}`(forge: Forge) {
        // Given
        val fakeDatadogContext: DatadogContext = forge.getForgery()

        // When
        val viewRecordsCount = testedFeaturesContextResolver.resolveViewRecordsCount(
            fakeDatadogContext,
            fakeViewId
        )

        // Then
        assertThat(viewRecordsCount).isEqualTo(0L)
    }
}
