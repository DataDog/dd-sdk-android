/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.android.webview.internal.rum.domain.RumContext
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
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebViewRumEventContextProviderTest {

    lateinit var testedContextProvider: WebViewRumEventContextProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeApplicationId: UUID

    @Forgery
    lateinit var fakeSessionId: UUID

    @StringForgery()
    lateinit var fakeSessionState: String

    @BeforeEach
    fun `set up`() {
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                put(
                    Feature.RUM_FEATURE_NAME,
                    mapOf(
                        "application_id" to fakeApplicationId.toString(),
                        "session_id" to fakeSessionId.toString(),
                        "session_state" to fakeSessionState
                    )
                )
            }
        )
        testedContextProvider = WebViewRumEventContextProvider(mockInternalLogger)
    }

    @Test
    fun `M return active context W getRumContext()`() {
        // Given
        val context = fakeDatadogContext

        // When
        val rumContext = checkNotNull(testedContextProvider.getRumContext(context))

        // Then
        assertThat(rumContext.applicationId)
            .isEqualTo(fakeApplicationId.toString())
        assertThat(rumContext.sessionId)
            .isEqualTo(fakeSessionId.toString())
    }

    @ParameterizedTest
    @EnumSource(UnavailableContext::class)
    fun `M recover correlation W getRumContext() {context becomes available again}`(
        unavailableContext: UnavailableContext
    ) {
        // Given
        val originalContext = checkNotNull(testedContextProvider.getRumContext(fakeDatadogContext))
        val partialContext = checkNotNull(fakeDatadogContext.featuresContext[Feature.RUM_FEATURE_NAME]).toMutableMap()
        when (unavailableContext) {
            UnavailableContext.MISSING_FEATURE -> partialContext.clear()
            UnavailableContext.APPLICATION_ONLY -> partialContext.keys.retainAll(setOf("application_id"))
            UnavailableContext.MISSING_APPLICATION -> partialContext.remove("application_id")
            UnavailableContext.ZERO_APPLICATION -> partialContext["application_id"] = RumContext.NULL_UUID
            UnavailableContext.MISSING_SESSION -> partialContext.remove("session_id")
            UnavailableContext.ZERO_SESSION -> partialContext["session_id"] = RumContext.NULL_UUID
            UnavailableContext.MISSING_STATE -> partialContext.remove("session_state")
            UnavailableContext.EMPTY_STATE -> partialContext["session_state"] = ""
        }
        val unavailableDatadogContext = fakeDatadogContext.copy(
            featuresContext = if (unavailableContext == UnavailableContext.MISSING_FEATURE) {
                emptyMap()
            } else {
                mapOf(Feature.RUM_FEATURE_NAME to partialContext)
            }
        )

        // When
        val unavailableRumContext = testedContextProvider.getRumContext(unavailableDatadogContext)
        val repeatedRumContext = testedContextProvider.getRumContext(unavailableDatadogContext)
        val restoredRumContext = testedContextProvider.getRumContext(fakeDatadogContext)

        // Then
        assertThat(unavailableRumContext).isNull()
        assertThat(repeatedRumContext).isNull()
        assertThat(restoredRumContext).isEqualTo(originalContext)
        if (unavailableContext in
            setOf(
                UnavailableContext.MISSING_FEATURE,
                UnavailableContext.MISSING_APPLICATION,
                UnavailableContext.ZERO_APPLICATION
            )
        ) {
            mockInternalLogger.verifyLog(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                WebViewRumEventContextProvider.RUM_NOT_INITIALIZED_WARNING_MESSAGE
            )
        } else {
            verifyNoInteractions(mockInternalLogger)
        }
    }

    enum class UnavailableContext {
        MISSING_FEATURE,
        APPLICATION_ONLY,
        MISSING_APPLICATION,
        ZERO_APPLICATION,
        MISSING_SESSION,
        ZERO_SESSION,
        MISSING_STATE,
        EMPTY_STATE
    }
}
