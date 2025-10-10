/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.attributes.ViewScopeInstrumentationType
import com.datadog.android.internal.attributes.enrichWithConstantAttribute
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class Navigation3Test {

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockAttributesResolver: AttributesResolver<String>

    @Mock
    lateinit var mockStackKeyResolver: BackStackKeyResolver<String>

    @StringForgery
    lateinit var fakeTopKey: String

    @StringForgery
    lateinit var fakeStableKey: String

    @BeforeEach
    fun `set up`() {
        // Default mock behaviour
        whenever(mockStackKeyResolver.getStableKey(fakeTopKey)) doReturn fakeStableKey
    }

    @Test
    fun `M start view W TrackBackStack() { isResumed = true, predicate accepts }`(
        @StringForgery fakeViewName: String
    ) {
        // Given
        val stubPredicate = StubComponentPredicate(viewName = fakeViewName)
        val mockAttributesResolver = mock<AttributesResolver<String>>()
        val expectedAttributes = emptyMap<String, Any?>()

        // When
        trackBackStack(
            topKey = fakeTopKey,
            isResumed = true,
            keyPredicate = stubPredicate,
            backStackKeyResolver = mockStackKeyResolver,
            attributesResolver = mockAttributesResolver,
            rumMonitor = mockRumMonitor,
            internalLogger = mockInternalLogger
        )

        // Then
        verify(mockRumMonitor).startView(
            fakeStableKey,
            fakeViewName,
            expectedAttributes.toMutableMap()
                .enrichWithConstantAttribute(ViewScopeInstrumentationType.COMPOSE)
        )
        verify(mockRumMonitor, never()).stopView(any(), any())
    }

    @Test
    fun `M stop view W TrackBackStack() { isResumed = false, predicate accepts }`() {
        // Given
        val stubPredicate = StubComponentPredicate()

        // When
        trackBackStack(
            topKey = fakeTopKey,
            isResumed = false,
            keyPredicate = stubPredicate,
            backStackKeyResolver = mockStackKeyResolver,
            attributesResolver = mockAttributesResolver,
            rumMonitor = mockRumMonitor,
            internalLogger = mockInternalLogger
        )

        // Then
        verify(mockRumMonitor, never()).startView(
            any(),
            any(),
            any()
        )
        verify(mockRumMonitor).stopView(fakeStableKey, emptyMap())
    }

    @Test
    fun `M not start view W TrackBackStack() {predicate does not accept }`(@BoolForgery isResumed: Boolean) {
        // Given
        val stubPredicate = StubComponentPredicate(accept = false)
        val mockAttributesResolver = mock<AttributesResolver<String>>()

        // When
        trackBackStack(
            topKey = fakeTopKey,
            isResumed = isResumed,
            keyPredicate = stubPredicate,
            backStackKeyResolver = mockStackKeyResolver,
            attributesResolver = mockAttributesResolver,
            rumMonitor = mockRumMonitor,
            internalLogger = mockInternalLogger
        )

        // Then
        verify(mockRumMonitor, never()).startView(
            any(),
            any(),
            any()
        )
    }

    private class StubComponentPredicate(
        private val accept: Boolean = true,
        private val viewName: String? = null
    ) : ComponentPredicate<String> {
        override fun accept(component: String): Boolean = accept
        override fun getViewName(component: String): String? = viewName
    }
}
