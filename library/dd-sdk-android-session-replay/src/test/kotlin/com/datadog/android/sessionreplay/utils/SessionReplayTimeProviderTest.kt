/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayTimeProviderTest {

    lateinit var testedTimeProvider: SessionReplayTimeProvider

    @BeforeEach
    fun `set up`() {
        testedTimeProvider = SessionReplayTimeProvider()
    }

    @Test
    fun `M return consistent timestamps W getDeviceTimestamp`(forge: Forge) {
        // When
        val timestamps: List<Long> = forge.aList { testedTimeProvider.getDeviceTimestamp() }

        // Then
        var prevTimestamp = timestamps[0]
        timestamps.forEach {
            assertThat(it).isGreaterThanOrEqualTo(prevTimestamp)
            prevTimestamp = it
        }
    }
}
