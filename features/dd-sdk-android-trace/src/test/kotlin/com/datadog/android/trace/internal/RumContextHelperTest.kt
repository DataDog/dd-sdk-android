/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.internal.concurrent.CompletableFuture
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.internal.RumContextHelper.DATADOG_INITIAL_CONTEXT
import com.datadog.android.trace.internal.RumContextHelper.INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR
import com.datadog.android.trace.internal.RumContextHelper.extractRumContextFeature
import com.datadog.android.trace.internal.RumContextHelper.injectRumContextFeature
import com.datadog.android.trace.utils.RumContextTestsUtils.RUM_CONTEXT_ACTION_ID
import com.datadog.android.trace.utils.RumContextTestsUtils.RUM_CONTEXT_APPLICATION_ID
import com.datadog.android.trace.utils.RumContextTestsUtils.RUM_CONTEXT_SESSION_ID
import com.datadog.android.trace.utils.RumContextTestsUtils.RUM_CONTEXT_VIEW_ID
import com.datadog.android.trace.utils.RumContextTestsUtils.aDatadogContextWithRumContext
import com.datadog.android.trace.utils.RumContextTestsUtils.aRumContext
import com.datadog.android.trace.utils.RumContextTestsUtils.thenReturnContext
import com.datadog.android.trace.utils.verifyLog
import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.core.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class RumContextHelperTest {

    @Mock
    private lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    private lateinit var mockSpanBuilder: DatadogSpanBuilder

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Forgery
    private lateinit var fakeDatadogContext: DatadogContext
    private lateinit var fakeRumContext: Map<String, Any?>
    private lateinit var fakeContextFeature: CompletableFuture<DatadogContext>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeContextFeature = CompletableFuture()
        fakeRumContext = forge.aRumContext()
        fakeDatadogContext = forge.aDatadogContextWithRumContext(fakeRumContext)
    }

    @Test
    fun `M withTag(DATADOG_INITIAL_CONTEXT) W injectRumContextFeature`() {
        // Given
        whenever(mockRumFeatureScope.withContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any()))
            .thenReturnContext(fakeDatadogContext)

        // When
        mockSpanBuilder.injectRumContextFeature(mockRumFeatureScope)

        // Then
        argumentCaptor<CompletableFuture<DatadogContext>> {
            verify(mockSpanBuilder).withTag(eq(DATADOG_INITIAL_CONTEXT), capture())
            assertThat(firstValue.value).isEqualTo(fakeDatadogContext)
        }
    }

    @Test
    fun `M log error if feature not complete W extractRumContextFeature { DDSpan }`() {
        // Given
        val span = mock<DDSpan> {
            on { getTag(DATADOG_INITIAL_CONTEXT) } doAnswer { fakeContextFeature }
        }

        // When
        span.extractRumContextFeature(mockInternalLogger)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.MAINTAINER),
            INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR
        )
    }

    @Test
    fun `M log error if feature not complete W extractRumContextFeature { DatadogSpan }`() {
        // Given
        val span = mock<DatadogSpan> {
            on { getTag(DATADOG_INITIAL_CONTEXT) } doAnswer { fakeContextFeature }
        }

        // When
        span.extractRumContextFeature(mockInternalLogger)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.MAINTAINER),
            INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR
        )
    }

    @Test
    fun `M set RUM tags W extractRumContextFeature { DatadogSpan }`() {
        // Given
        fakeContextFeature.complete(fakeDatadogContext)
        val span = mock<DatadogSpan> {
            on { getTag(DATADOG_INITIAL_CONTEXT) } doAnswer { fakeContextFeature }
        }

        // When
        span.extractRumContextFeature(mockInternalLogger)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verify(span).setTag(LogAttributes.RUM_APPLICATION_ID, fakeRumContext[RUM_CONTEXT_APPLICATION_ID])
        verify(span).setTag(LogAttributes.RUM_SESSION_ID, fakeRumContext[RUM_CONTEXT_SESSION_ID])
        verify(span).setTag(LogAttributes.RUM_VIEW_ID, fakeRumContext[RUM_CONTEXT_VIEW_ID])
        verify(span).setTag(LogAttributes.RUM_ACTION_ID, fakeRumContext[RUM_CONTEXT_ACTION_ID])
        verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)

        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M set RUM tags W extractRumContextFeature { DDSpan }`() {
        // Given
        fakeContextFeature.complete(fakeDatadogContext)
        val span = mock<DDSpan> {
            on { getTag(DATADOG_INITIAL_CONTEXT) } doAnswer { fakeContextFeature }
        }

        // When
        span.extractRumContextFeature(mockInternalLogger)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verify(span).setTag(LogAttributes.RUM_APPLICATION_ID, fakeRumContext[RUM_CONTEXT_APPLICATION_ID])
        verify(span).setTag(LogAttributes.RUM_SESSION_ID, fakeRumContext[RUM_CONTEXT_SESSION_ID])
        verify(span).setTag(LogAttributes.RUM_VIEW_ID, fakeRumContext[RUM_CONTEXT_VIEW_ID])
        verify(span).setTag(LogAttributes.RUM_ACTION_ID, fakeRumContext[RUM_CONTEXT_ACTION_ID])
        verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)

        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M set null RUM tags W extractRumContextFeature { DatadogSpan, rum context is empty }`(forge: Forge) {
        // Given
        fakeContextFeature.complete(forge.aDatadogContextWithRumContext(emptyMap()))
        val span = mock<DatadogSpan> {
            on { getTag(DATADOG_INITIAL_CONTEXT) } doAnswer { fakeContextFeature }
        }

        // When
        span.extractRumContextFeature(mockInternalLogger)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verify(span).setTag(LogAttributes.RUM_APPLICATION_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_SESSION_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_VIEW_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_ACTION_ID, null as Any?)
        verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)

        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M set null RUM tags W extractRumContextFeature { DDScope, rum context is empty }`(forge: Forge) {
        // Given
        fakeContextFeature.complete(forge.aDatadogContextWithRumContext(emptyMap()))
        val span = mock<DDSpan> {
            on { getTag(DATADOG_INITIAL_CONTEXT) } doAnswer { fakeContextFeature }
        }

        // When
        span.extractRumContextFeature(mockInternalLogger)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verify(span).setTag(LogAttributes.RUM_APPLICATION_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_SESSION_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_VIEW_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_ACTION_ID, null as Any?)
        verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)

        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M not call setTag W getTag(DATADOG_INITIAL_CONTEXT) is null { DatadogSpan }`() {
        // Given
        fakeContextFeature.complete(fakeDatadogContext)
        val span = mock<DatadogSpan> {
            on { getTag(DATADOG_INITIAL_CONTEXT) } doAnswer { null }
        }

        // When
        span.extractRumContextFeature(mockInternalLogger)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M not call setTag W getTag(DATADOG_INITIAL_CONTEXT) is null { DDSpan }`() {
        // Given
        fakeContextFeature.complete(fakeDatadogContext)
        val span = mock<DDSpan> {
            on { getTag(DATADOG_INITIAL_CONTEXT) } doAnswer { null }
        }

        // When
        span.extractRumContextFeature(mockInternalLogger)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }
}
