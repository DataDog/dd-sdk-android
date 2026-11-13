/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.internal.DatadogFlagsClient
import com.datadog.android.flags.internal.NoOpFlagsClient
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsInternalProxyLoggerTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeClientName: String

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
    }

    @Test
    fun `M return the core's logger W getInternalLogger() {Datadog client}`() {
        // Given
        val client = mock<DatadogFlagsClient> {
            on { featureSdkCore } doReturn mockSdkCore
        }

        // When
        val logger = _FlagsInternalProxy(client).getInternalLogger()

        // Then
        assertThat(logger).isSameAs(mockInternalLogger)
    }

    @Test
    fun `M return the core's logger W getInternalLogger() {no-op client}`(@StringForgery fakeReason: String) {
        // Given
        val client = NoOpFlagsClient(fakeClientName, fakeReason, mockSdkCore) { _, _ -> }

        // When
        val logger = _FlagsInternalProxy(client).getInternalLogger()

        // Then
        assertThat(logger).isSameAs(mockInternalLogger)
    }

    @Test
    fun `M return the unbound logger W getInternalLogger() {application client}`() {
        // When
        val logger = _FlagsInternalProxy(mock<FlagsClient>()).getInternalLogger()

        // Then
        assertThat(logger).isSameAs(InternalLogger.UNBOUND)
    }
}
