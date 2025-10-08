/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.Flags.FLAGS_EXECUTOR_NAME
import com.datadog.android.flags.featureflags.FlagsClient
import com.datadog.android.flags.featureflags.internal.NoOpFlagsClient
import com.datadog.android.flags.internal.FlagsFeature
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsTest {

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    @StringForgery
    lateinit var fakeClientToken: String

    @StringForgery
    lateinit var fakeEnv: String

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.createSingleThreadExecutorService(FLAGS_EXECUTOR_NAME)) doReturn mockExecutorService

        whenever(mockDatadogContext.clientToken) doReturn fakeClientToken
        whenever(mockDatadogContext.site) doReturn DatadogSite.US1
        whenever(mockDatadogContext.env) doReturn fakeEnv
        whenever(mockSdkCore.getDatadogContext()) doReturn mockDatadogContext
    }

    // region enable()

    @Test
    fun `M register FlagsFeature W enable()`() {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .setTrackExposures(true)
            .build()

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.name).isEqualTo(FLAGS_FEATURE_NAME)
        }
    }

    @Test
    fun `M register FlagsClient W enable() { valid context }`() {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .setTrackExposures(false)
            .build()

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        // Verify that a real client was registered by checking it's not a no-op
        val client = FlagsClient.get(mockSdkCore)
        assertThat(client).isNotInstanceOf(NoOpFlagsClient::class.java)
    }

    @Test
    fun `M not register FlagsClient W enable() { missing context }`() {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .setTrackExposures(false)
            .build()

        whenever(mockSdkCore.getDatadogContext()) doReturn null

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        // Verify that no real client was registered by checking it's a no-op
        val client = FlagsClient.get(mockSdkCore)
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
    }

    @Test
    fun `M log error W enable() { missing all context parameters }`() {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .setTrackExposures(false)
            .build()

        whenever(mockSdkCore.getDatadogContext()) doReturn null

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        argumentCaptor<() -> String> {
            verify(mockSdkCore.internalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.MAINTAINER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(lastValue()).isEqualTo("Missing required configuration parameters: clientToken, site, env")
        }
    }

    @Test
    fun `M log error W enable() { missing clientToken and site }`() {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .setTrackExposures(false)
            .build()

        whenever(mockDatadogContext.clientToken).thenReturn(null)
        whenever(mockDatadogContext.site).thenReturn(null)
        whenever(mockDatadogContext.env) doReturn fakeEnv
        whenever(mockSdkCore.getDatadogContext()) doReturn mockDatadogContext

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        argumentCaptor<() -> String> {
            verify(mockSdkCore.internalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.MAINTAINER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(lastValue()).isEqualTo("Missing required configuration parameters: clientToken, site")
        }
    }

    @Test
    fun `M create executor service W enable()`() {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .setTrackExposures(false)
            .build()

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        verify(mockSdkCore).createSingleThreadExecutorService(FLAGS_EXECUTOR_NAME)
    }

    // endregion
}
