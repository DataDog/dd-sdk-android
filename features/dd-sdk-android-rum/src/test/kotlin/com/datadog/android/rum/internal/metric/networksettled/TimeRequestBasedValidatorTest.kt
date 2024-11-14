/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric.networksettled

import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class TimeRequestBasedValidatorTest {

    private lateinit var testedValidator: TimeBasedInitialResourceIdentifier

    @Forgery
    lateinit var fakeNetworkSettledResourceContext: NetworkSettledResourceContext

    private var fakeIntervalThreshold: Long = 0L
    private var fakeViewCreatedTimestamp: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeViewCreatedTimestamp = forge.aTinyPositiveLong()
        fakeIntervalThreshold = forge.aTinyPositiveLong()
        fakeNetworkSettledResourceContext =
            fakeNetworkSettledResourceContext.copy(
                viewCreatedTimestamp = fakeViewCreatedTimestamp
            )
        testedValidator = TimeBasedInitialResourceIdentifier(fakeIntervalThreshold)
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
                fakeViewCreatedTimestamp + fakeIntervalThreshold + forge.aTinyPositiveLong()
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
                fakeViewCreatedTimestamp + forge.aLong(min = 0, max = fakeIntervalThreshold)
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
