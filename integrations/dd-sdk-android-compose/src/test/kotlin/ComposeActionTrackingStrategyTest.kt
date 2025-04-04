/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import android.view.View
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComposeActionTrackingStrategyTest {

    private lateinit var testedComposeActionTrackingStrategy: ComposeActionTrackingStrategy

    @Mock
    private lateinit var mockDecorView: View

    @BeforeEach
    fun `set up`() {
        testedComposeActionTrackingStrategy = ComposeActionTrackingStrategy()
    }

    // TODO RUM-9298: Fix the test after implementing Compose action tracking strategy.
    @Test
    fun `M return null W ComposeActionTrackingStrategy is not implemented yet {tap}`(forge: Forge) {
        // Given
        val x = forge.aFloat()
        val y = forge.aFloat()

        // When
        val result = testedComposeActionTrackingStrategy.findTargetForTap(mockDecorView, x, y)

        // Then
        assertThat(result).isNull()
    }

    // TODO RUM-9298: Fix the test after implementing Compose action tracking strategy.
    @Test
    fun `M return null W ComposeActionTrackingStrategy is not implemented yet {scroll}`(forge: Forge) {
        // Given
        val x = forge.aFloat()
        val y = forge.aFloat()

        // When
        val result = testedComposeActionTrackingStrategy.findTargetForScroll(mockDecorView, x, y)

        // Then
        assertThat(result).isNull()
    }
}
