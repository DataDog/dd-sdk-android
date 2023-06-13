/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import android.content.Context
import com.datadog.android.event.EventMapper
import com.datadog.android.event.NoOpEventMapper
import com.datadog.android.log.internal.net.LogsRequestFactory
import com.datadog.android.log.model.LogEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogsFeatureBuilderTest {

    private val testedBuilder: LogsFeature.Builder = LogsFeature.Builder()

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`(
        @StringForgery fakePackageName: String
    ) {
        // When
        val logsFeature = testedBuilder.build()
        logsFeature.onInitialize(
            mockSdkCore,
            appContext = mock<Context>().apply { whenever(packageName) doReturn fakePackageName }
        )

        // Then
        val requestFactory = logsFeature.requestFactory
        assertThat(requestFactory).isInstanceOf(LogsRequestFactory::class.java)
        assertThat((requestFactory as LogsRequestFactory).customEndpointUrl)
            .isNull()

        assertThat(logsFeature.eventMapper).isInstanceOf(NoOpEventMapper::class.java)
    }

    @Test
    fun `𝕄 build feature with custom site 𝕎 useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") logsEndpointUrl: String,
        @StringForgery fakePackageName: String
    ) {
        // When
        val logsFeature = testedBuilder.useCustomEndpoint(logsEndpointUrl).build()
        logsFeature.onInitialize(
            mockSdkCore,
            appContext = mock<Context>().apply { whenever(packageName) doReturn fakePackageName }
        )

        // Then
        val requestFactory = logsFeature.requestFactory
        assertThat(requestFactory).isInstanceOf(LogsRequestFactory::class.java)
        assertThat((requestFactory as LogsRequestFactory).customEndpointUrl)
            .isEqualTo(logsEndpointUrl)
    }

    @Test
    fun `𝕄 build feature with Log eventMapper 𝕎 setLogEventMapper() and build()`() {
        // Given
        val mockEventMapper: EventMapper<LogEvent> = mock()

        // When
        val logsFeature = testedBuilder
            .setLogEventMapper(mockEventMapper)
            .build()

        // Then
        assertThat(logsFeature.eventMapper).isEqualTo(mockEventMapper)
    }
}
