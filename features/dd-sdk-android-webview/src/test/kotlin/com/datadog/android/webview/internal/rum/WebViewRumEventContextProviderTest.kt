/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.internal.rum.RumSessionState
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
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

    @StringForgery
    lateinit var fakeApplicationId: String

    @StringForgery
    lateinit var fakeSessionId: String

    @BeforeEach
    fun `set up`() {
        testedContextProvider = WebViewRumEventContextProvider(mockInternalLogger)
    }

    @Test
    fun `M return the active context W getRumContext()`(
        @Forgery fakeSessionState: RumSessionState,
        @BoolForgery fakeHasTimeseries: Boolean
    ) {
        // Given
        val fakeContext = fakeDatadogContext.copy(
            featuresContext = mapOf(
                Feature.RUM_FEATURE_NAME to mapOf(
                    FeatureContextKeys.RUM_CONTEXT to RumContext(
                        applicationId = fakeApplicationId,
                        sessionId = fakeSessionId,
                        sessionState = fakeSessionState,
                        hasTimeseries = fakeHasTimeseries
                    )
                )
            )
        )

        // When
        val rumContext = checkNotNull(testedContextProvider.getRumContext(fakeContext))

        // Then
        assertThat(rumContext.applicationId).isEqualTo(fakeApplicationId)
        assertThat(rumContext.sessionId).isEqualTo(fakeSessionId)
        assertThat(rumContext.sessionState).isEqualTo(fakeSessionState.asString)
        assertThat(rumContext.hasTimeseries).isEqualTo(fakeHasTimeseries)
    }

    @ParameterizedTest
    @EnumSource(InvalidContext::class)
    fun `M return null and warn once W getRumContext {invalid context}`(invalidContext: InvalidContext) {
        // Given
        val fakeRumContext = when (invalidContext) {
            InvalidContext.NO_SNAPSHOT -> null
            InvalidContext.NO_APPLICATION -> RumContext(sessionId = fakeSessionId)
            InvalidContext.NO_SESSION -> RumContext(applicationId = fakeApplicationId)
        }
        val fakeContext = fakeDatadogContext.copy(
            featuresContext = mapOf(
                Feature.RUM_FEATURE_NAME to mapOf(FeatureContextKeys.RUM_CONTEXT to fakeRumContext)
            )
        )

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
