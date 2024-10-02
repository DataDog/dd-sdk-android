/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.prerequisite

import com.datadog.android.sessionreplay.SystemRequirementsConfiguration
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
class SystemRequirementsConfigurationTest {

    @IntForgery
    private var fakeMemorySize: Int = 0

    @IntForgery
    private var fakeCpuCores: Int = 0

    @Test
    fun `M have correct default value W build()`() {
        // Given
        val builder = SystemRequirementsConfiguration.Builder()

        // When
        val result = builder.build()

        // Then
        assertThat(result.minCPUCores).isZero()
        assertThat(result.minRAMSizeMb).isZero()
    }

    @Test
    fun `M have 0 value W use NONE`() {
        // Given
        val result = SystemRequirementsConfiguration.NONE

        // Then
        assertThat(result.minCPUCores).isZero()
        assertThat(result.minRAMSizeMb).isZero()
    }

    @Test
    fun `M build correct instance W build()`() {
        // Given
        val builder = SystemRequirementsConfiguration.Builder()

        // When
        val result = builder
            .setMinRAMSizeMb(fakeMemorySize)
            .setMinCPUCoreNumber(fakeCpuCores)
            .build()

        // Then
        assertThat(result.minCPUCores).isEqualTo(fakeCpuCores)
        assertThat(result.minRAMSizeMb).isEqualTo(fakeMemorySize)
    }
}
