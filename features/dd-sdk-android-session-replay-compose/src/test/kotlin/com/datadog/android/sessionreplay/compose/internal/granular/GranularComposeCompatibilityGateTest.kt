/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class GranularComposeCompatibilityGateTest {

    private val testedGate = GranularComposeCompatibilityGate()

    @Test
    fun `M be available W isAvailable() { fresh gate }`() {
        // Then
        assertThat(testedGate.isAvailable()).isTrue()
    }

    @Test
    fun `M no longer be available W markIncompatible()`(
        @StringForgery fakeMessage: String
    ) {
        // When
        testedGate.markIncompatible(Throwable(fakeMessage), mock<InternalLogger>())

        // Then
        assertThat(testedGate.isAvailable()).isFalse()
    }

    @Test
    fun `M stay unavailable W markIncompatible() { called again }`(
        @StringForgery fakeFirstMessage: String,
        @StringForgery fakeSecondMessage: String
    ) {
        // Given
        val mockLogger: InternalLogger = mock()
        testedGate.markIncompatible(Throwable(fakeFirstMessage), mockLogger)

        // When
        testedGate.markIncompatible(Throwable(fakeSecondMessage), mockLogger)

        // Then - never re-attempted: the monotonic state never downgrades and the second failure
        // is deduplicated by the logger's own onlyOnce handling, not by this class re-checking.
        assertThat(testedGate.isAvailable()).isFalse()
    }

    @Test
    fun `M log telemetry once W markIncompatible()`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val mockLogger: InternalLogger = mock()
        val fakeThrowable = Throwable(fakeMessage)

        // When
        testedGate.markIncompatible(fakeThrowable, mockLogger)

        // Then
        verify(mockLogger).log(
            level = eq(InternalLogger.Level.ERROR),
            target = eq(InternalLogger.Target.TELEMETRY),
            messageBuilder = any(),
            throwable = eq(fakeThrowable),
            onlyOnce = eq(true),
            additionalProperties = eq(null)
        )
    }
}
