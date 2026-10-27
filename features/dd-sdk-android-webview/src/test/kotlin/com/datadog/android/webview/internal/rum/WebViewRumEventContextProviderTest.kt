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
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions

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
    @EnumSource(UnavailableContext::class)
    fun `M recover correlation W getRumContext {context becomes available again}`(
        unavailableContext: UnavailableContext
    ) {
        // Given
        val originalContext = fakeDatadogContext.withRumContext(fakeRumContext)
        val partialRumContext = when (unavailableContext) {
            UnavailableContext.NO_SNAPSHOT -> null
            UnavailableContext.ZERO_APPLICATION -> fakeRumContext.copy(applicationId = RumContext.NULL_UUID)
            UnavailableContext.APPLICATION_ONLY -> RumContext(applicationId = fakeRumContext.applicationId)
        }
        val partialContext = fakeDatadogContext.withRumContext(partialRumContext)
        val original = testedContextProvider.getRumContext(originalContext)

        // When
        val unavailable = testedContextProvider.getRumContext(partialContext)
        val repeated = testedContextProvider.getRumContext(partialContext)
        val restored = testedContextProvider.getRumContext(originalContext)

        // Then
        assertThat(restored).isEqualTo(original)
        assertThat(repeated).isEqualTo(unavailable)
        if (unavailableContext == UnavailableContext.NO_SNAPSHOT ||
            unavailableContext == UnavailableContext.ZERO_APPLICATION
        ) {
            assertThat(unavailable).isNull()
            mockInternalLogger.verifyLog(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                WebViewRumEventContextProvider.RUM_NOT_INITIALIZED_WARNING_MESSAGE,
                onlyOnce = true,
                mode = times(2)
            )
        } else {
            val context = checkNotNull(unavailable)
            assertThat(context.applicationId).isEqualTo(fakeRumContext.applicationId)
            assertThat(context.session).isNull()
            verifyNoInteractions(mockInternalLogger)
        }
    }

    enum class UnavailableContext {
        NO_SNAPSHOT,
        ZERO_APPLICATION,
        APPLICATION_ONLY
    }
}
