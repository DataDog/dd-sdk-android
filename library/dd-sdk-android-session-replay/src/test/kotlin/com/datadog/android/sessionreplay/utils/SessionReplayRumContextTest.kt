/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import fr.xgouchet.elmyr.annotation.Forgery
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
class SessionReplayRumContextTest {

    @Test
    fun `M return true W isNotValid() { RumContext is not valid }`() {
        // Given
        val rumContext = SessionReplayRumContext()

        // Then
        assertThat(rumContext.isNotValid()).isTrue
    }

    @Test
    fun `M return false W isNotValid() { RumContext is valid }`(
        @Forgery fakeContext: SessionReplayRumContext
    ) {
        assertThat(fakeContext.isNotValid()).isFalse()
    }
}
