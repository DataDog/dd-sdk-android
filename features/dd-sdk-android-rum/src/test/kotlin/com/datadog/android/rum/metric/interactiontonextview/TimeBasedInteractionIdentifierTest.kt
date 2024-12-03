/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.metric.interactiontonextview

import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TimeBasedInteractionIdentifierTest {

    private lateinit var testedIdentifier: TimeBasedInteractionIdentifier

    private var fakeTimestampThresholdInNanos: Long = 0L
    private var fakeTimestampThresholdInMs: Long = 0L

    // region setUp

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTimestampThresholdInMs = forge.aLong(min = 3000, max = 10000)
        fakeTimestampThresholdInNanos = TimeUnit.MILLISECONDS.toNanos(fakeTimestampThresholdInMs)
        testedIdentifier = TimeBasedInteractionIdentifier(fakeTimestampThresholdInMs)
    }

    // endregion

    // region Unit Tests

    @Test
    fun `M return true W validate { valid context }`(forge: Forge) {
        // Given
        val viewCreatedTimestamp = System.nanoTime()
        val eventCreatedTimestamp = viewCreatedTimestamp + forge.aLong(min = 0, max = fakeTimestampThresholdInNanos)
        val fakeValidContext = forge.getForgery(PreviousViewLastInteractionContext::class.java).copy(
            eventCreatedAtNanos = eventCreatedTimestamp,
            currentViewCreationTimestamp = viewCreatedTimestamp
        )

        // When
        val result = testedIdentifier.validate(fakeValidContext)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W validate { invalid context }`(forge: Forge) {
        // Given
        val viewCreatedTimestamp = System.nanoTime()
        val eventCreatedTimestamp = viewCreatedTimestamp + forge.aLong(
            min = fakeTimestampThresholdInNanos + 1,
            max = fakeTimestampThresholdInNanos + 10000
        )
        val fakeValidContext = forge.getForgery(PreviousViewLastInteractionContext::class.java).copy(
            eventCreatedAtNanos = eventCreatedTimestamp,
            currentViewCreationTimestamp = viewCreatedTimestamp
        )

        // When
        val result = testedIdentifier.validate(fakeValidContext)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W validate { context without viewCreatedTimestamp }`(forge: Forge) {
        // Given
        val fakeValidContext =
            forge.getForgery(PreviousViewLastInteractionContext::class.java).copy(currentViewCreationTimestamp = null)

        // When
        val result = testedIdentifier.validate(fakeValidContext)

        // Then
        assertThat(result).isFalse()
    }

    // endregion
}
