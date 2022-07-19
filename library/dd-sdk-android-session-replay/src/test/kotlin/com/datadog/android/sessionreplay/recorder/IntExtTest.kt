/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import com.datadog.android.sessionreplay.utils.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
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
@ForgeConfiguration(ForgeConfigurator::class)
internal class IntExtTest {

    @RepeatedTest(100)
    fun `M correctly normalize an Int W densityNormalized()`(
        @IntForgery fakeInt: Int,
        @FloatForgery(min = 1.0f, max = 100.0f)
        fakeDensity: Float
    ) {
        assertThat(fakeInt.densityNormalized(fakeDensity))
            .isEqualTo((fakeInt / fakeDensity).toInt())
    }

    @Test
    fun `M correctly handle division by 0 W densityNormalized() {density is 0}`(
        @IntForgery fakeInt: Int
    ) {
        assertThat(fakeInt.densityNormalized(0f)).isEqualTo(fakeInt)
    }
}
