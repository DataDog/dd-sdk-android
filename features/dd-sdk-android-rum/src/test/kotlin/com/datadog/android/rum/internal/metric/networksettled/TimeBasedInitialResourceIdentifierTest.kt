/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric.networksettled

import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.ObjectTest
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class TimeBasedInitialResourceIdentifierTest : ObjectTest<TimeBasedInitialResourceIdentifier>() {

    private lateinit var testedValidator: TimeBasedInitialResourceIdentifier

    @Forgery
    lateinit var fakeNetworkSettledResourceContext: NetworkSettledResourceContext

    private var fakeIntervalThresholdInMs: Long = 0L
    private var fakeIntervalThresholdInNanos: Long = 0L
    private var fakeViewCreatedTimestamp: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeViewCreatedTimestamp = forge.aTinyPositiveLong()
        fakeIntervalThresholdInMs = forge.aTinyPositiveLong()
        fakeIntervalThresholdInNanos = TimeUnit.MILLISECONDS.toNanos(fakeIntervalThresholdInMs)
        fakeNetworkSettledResourceContext =
            fakeNetworkSettledResourceContext.copy(
                viewCreatedTimestamp = fakeViewCreatedTimestamp
            )
        testedValidator = TimeBasedInitialResourceIdentifier(fakeIntervalThresholdInMs)
    }

    override fun createInstance(forge: Forge): TimeBasedInitialResourceIdentifier {
        return TimeBasedInitialResourceIdentifier(fakeIntervalThresholdInMs)
    }

    override fun createEqualInstance(
        source: TimeBasedInitialResourceIdentifier,
        forge: Forge
    ): TimeBasedInitialResourceIdentifier {
        return TimeBasedInitialResourceIdentifier(fakeIntervalThresholdInMs)
    }

    override fun createUnequalInstance(
        source: TimeBasedInitialResourceIdentifier,
        forge: Forge
    ): TimeBasedInitialResourceIdentifier? {
        return TimeBasedInitialResourceIdentifier(fakeIntervalThresholdInMs + forge.aTinyPositiveLong())
    }

    // region Tests

    @Test
    fun `M return false W viewCreatedTimestamp is null`() {
        // Given
        fakeNetworkSettledResourceContext = fakeNetworkSettledResourceContext.copy(viewCreatedTimestamp = null)

        // When
        val result = testedValidator.validate(fakeNetworkSettledResourceContext)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W resource started after the threshold`(forge: Forge) {
        // Given
        fakeNetworkSettledResourceContext =
            fakeNetworkSettledResourceContext.copy(
                eventCreatedAtNanos =
                fakeViewCreatedTimestamp + fakeIntervalThresholdInNanos + forge.aTinyPositiveLong()
            )

        // When
        val result = testedValidator.validate(fakeNetworkSettledResourceContext)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W resource started before the threshold`(forge: Forge) {
        // Given
        fakeNetworkSettledResourceContext =
            fakeNetworkSettledResourceContext.copy(
                eventCreatedAtNanos =
                fakeViewCreatedTimestamp + forge.aLong(min = 0, max = fakeIntervalThresholdInNanos)
            )

        // When
        val result = testedValidator.validate(fakeNetworkSettledResourceContext)

        // Then
        assertThat(result).isTrue()
    }

    // endregion

    // region Internal

    private fun Forge.aTinyPositiveLong(): Long {
        return aLong(min = 1, max = 100000)
    }

    // endregion
}
