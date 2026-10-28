/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.android.utils.withRumContext
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verifyNoMoreInteractions

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class WebViewRumEventContextProviderTest {

    lateinit var testedContextProvider: WebViewRumEventContextProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeRumContext: RumContext

    @BeforeEach
    fun `set up`() {
        testedContextProvider = WebViewRumEventContextProvider(mockInternalLogger)
    }

    @Test
    fun `M return the active context W getRumContext()`() {
        // Given
        val fakeContext = fakeDatadogContext.withRumContext(fakeRumContext)

        // When
        val rumContext = testedContextProvider.getRumContext(fakeContext)

        // Then
        assertThat(rumContext).isSameAs(fakeRumContext)
    }

    @ParameterizedTest
    @EnumSource(InvalidContext::class)
    fun `M return null and warn once W getRumContext {invalid context}`(invalidContext: InvalidContext) {
        // Given
        val fakeInvalidContext = when (invalidContext) {
            InvalidContext.NO_SNAPSHOT -> null
            InvalidContext.NO_APPLICATION -> fakeRumContext.copy(applicationId = RumContext.NULL_UUID)
            InvalidContext.NO_SESSION -> fakeRumContext.copy(sessionId = RumContext.NULL_UUID)
        }
        val fakeContext = fakeDatadogContext.withRumContext(fakeInvalidContext)

        // When
        val firstContext = testedContextProvider.getRumContext(fakeContext)
        val secondContext = testedContextProvider.getRumContext(fakeContext)

        // Then
        assertThat(firstContext).isNull()
        assertThat(secondContext).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            WebViewRumEventContextProvider.RUM_NOT_INITIALIZED_WARNING_MESSAGE
        )
        verifyNoMoreInteractions(mockInternalLogger)
    }

    enum class InvalidContext {
        NO_SNAPSHOT,
        NO_APPLICATION,
        NO_SESSION
    }
}
