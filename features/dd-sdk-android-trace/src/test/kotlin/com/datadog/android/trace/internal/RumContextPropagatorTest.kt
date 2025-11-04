/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.AccountInfo
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.UserInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.SdkFeatureMock
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.internal.RumContextPropagator.Companion.DATADOG_INITIAL_CONTEXT
import com.datadog.android.trace.internal.RumContextPropagator.Companion.ERROR_FUTURE_GET_FAILED
import com.datadog.android.trace.internal.RumContextPropagator.Companion.INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR
import com.datadog.android.trace.internal.RumContextPropagator.Companion.extractRumContext
import com.datadog.android.trace.internal.RumContextPropagator.Companion.injectRumContext
import com.datadog.android.trace.utils.RumContextTestsUtils.RUM_CONTEXT_ACTION_ID
import com.datadog.android.trace.utils.RumContextTestsUtils.RUM_CONTEXT_APPLICATION_ID
import com.datadog.android.trace.utils.RumContextTestsUtils.RUM_CONTEXT_SESSION_ID
import com.datadog.android.trace.utils.RumContextTestsUtils.RUM_CONTEXT_VIEW_ID
import com.datadog.android.trace.utils.RumContextTestsUtils.aDatadogContextWithRumContext
import com.datadog.android.trace.utils.RumContextTestsUtils.aRumContext
import com.datadog.android.trace.utils.verifyLog
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.completedFutureMock
import com.datadog.tools.unit.completedWithErrorFutureMock
import com.datadog.tools.unit.forge.anException
import com.datadog.tools.unit.incompleteFutureMock
import com.datadog.trace.core.DDSpan
import com.datadog.trace.core.propagation.HttpCodec
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness
import org.mockito.verification.VerificationMode
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class RumContextPropagatorTest {

    private lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    private lateinit var mockSpanBuilder: DatadogSpanBuilder

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    private lateinit var fakeUserInfo: UserInfo
    private var fakeAccountInfo: AccountInfo? = null

    @Forgery
    private lateinit var fakeDatadogContext: DatadogContext
    private lateinit var fakeRumContext: Map<String, Any?>

    private val mockSdkCore = mock<InternalSdkCore> {
        on { getFeature(Feature.RUM_FEATURE_NAME) } doAnswer { mockRumFeatureScope }
        on { internalLogger } doAnswer { mockInternalLogger }
    }

    private val testedRumContextPropagator = RumContextPropagator { mockSdkCore }

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeAccountInfo = forge.aNullable { AccountInfo(id = forge.aString()) }
        fakeUserInfo = UserInfo(id = forge.aString())
        mockRumFeatureScope = SdkFeatureMock.create()
        fakeRumContext = forge.aRumContext()
        fakeDatadogContext = forge.aDatadogContextWithRumContext(fakeRumContext, fakeAccountInfo, fakeUserInfo)
    }

    @Test
    fun `M get(Long, TimeUnit) W extractRumContext {DDSpan, block=True}`() {
        // Given
        val futureMock = incompleteFutureMock<DatadogContext>()
        val span = newDDSpanWithLazyDatadogContext(futureMock)

        // When
        span.extractRumContext(testedRumContextPropagator, block = true)

        // Then
        verify(futureMock).get(1, TimeUnit.SECONDS)
    }

    @Test
    fun `M get(Long, TimeUnit) W extractRumContext {DatadogSpan, block=True}`() {
        // Given
        val futureMock = incompleteFutureMock<DatadogContext>()
        val span = newDatadogSpanWithLazyDatadogContext(futureMock)

        // When
        span.extractRumContext(testedRumContextPropagator, block = true)

        // Then
        verify(futureMock).get(1, TimeUnit.SECONDS)
    }

    @Test
    fun `M not block W extractRumContext {DDSpan, isDone=true, block=False}`() {
        // Given
        val futureMock = completedFutureMock<DatadogContext?>(null)
        val span = newDDSpanWithLazyDatadogContext(futureMock)

        // When
        span.extractRumContext(mock(), block = false)

        // Then
        inOrder(futureMock) {
            verify(futureMock).isDone
            verify(futureMock).get()
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M not block W extractRumContext {DatadogSpan, isDone=true, block=False}`() {
        // Given
        val futureMock = completedFutureMock<DatadogContext?>(null)
        val span = newDatadogSpanWithLazyDatadogContext(futureMock)

        // When
        span.extractRumContext(mock(), block = false)

        // Then
        inOrder(futureMock) {
            verify(futureMock).isDone
            verify(futureMock).get()
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M not block W extractRumContext {DDSpan, isDone=false, block=False}`() {
        // Given
        val futureMock = incompleteFutureMock<DatadogContext>()
        val span = newDDSpanWithLazyDatadogContext(futureMock)

        // When
        span.extractRumContext(testedRumContextPropagator, block = false)

        // Then
        inOrder(futureMock) {
            verify(futureMock).isDone
            verify(futureMock, never()).get()
            verifyNoMoreInteractions()
        }
        mockInternalLogger.verifyErrorLogged(INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR)
        verifyRumContextNotExtracted(span)
    }

    @Test
    fun `M not block W extractRumContext {DatadogSpan, isDone=false, block=False}`() {
        // Given
        val futureMock = incompleteFutureMock<DatadogContext>()
        val span = newDatadogSpanWithLazyDatadogContext(futureMock)

        // When
        span.extractRumContext(testedRumContextPropagator, block = false)

        // Then
        inOrder(futureMock) {
            verify(futureMock).isDone
            verify(futureMock, never()).get()
            verifyNoMoreInteractions()
        }
        mockInternalLogger.verifyErrorLogged(INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR)
        verifyRumContextNotExtracted(span)
    }

    @Test
    fun `M log ERROR_FUTURE_GET_FAILED if Future#get(Long, TimeUnit) failed { DDSpan, block = true }`(forge: Forge) {
        // Given
        val span = newDDSpanWithLazyDatadogContext(
            completedWithErrorFutureMock(forge.anException())
        )

        // When
        span.extractRumContext(testedRumContextPropagator, block = true)

        // Then
        mockInternalLogger.verifyErrorLogged(ERROR_FUTURE_GET_FAILED, mode = atLeastOnce())
        verifyRumContextNotExtracted(span)
    }

    @Test
    fun `M log ERROR_FUTURE_GET_FAILED if Future#get(Long, TimeUnit) failed { DatadogSpan, block = true }`(
        forge: Forge
    ) {
        // Given
        val span = newDatadogSpanWithLazyDatadogContext(
            completedWithErrorFutureMock(forge.anException())
        )

        // When
        span.extractRumContext(testedRumContextPropagator, block = true)

        // Then
        mockInternalLogger.verifyErrorLogged(ERROR_FUTURE_GET_FAILED, mode = atLeastOnce())
        verifyRumContextNotExtracted(span)
    }

    @Test
    fun `M log ERROR_FUTURE_GET_FAILED if Future#get() failed { DDSpan, block = false }`(forge: Forge) {
        // Given
        val span = newDatadogSpanWithLazyDatadogContext(
            completedWithErrorFutureMock(forge.anException())
        )

        // When
        span.extractRumContext(testedRumContextPropagator, block = false)

        // Then
        mockInternalLogger.verifyErrorLogged(ERROR_FUTURE_GET_FAILED, mode = atLeastOnce())
        verifyRumContextNotExtracted(span)
    }

    @Test
    fun `M log ERROR_FUTURE_GET_FAILED if Future#get() failed { DatadogSpan, block = false }`(forge: Forge) {
        // Given
        val span = newDatadogSpanWithLazyDatadogContext(
            completedWithErrorFutureMock(forge.anException())
        )

        // When
        span.extractRumContext(testedRumContextPropagator, block = false)

        // Then
        mockInternalLogger.verifyErrorLogged(ERROR_FUTURE_GET_FAILED, mode = atLeastOnce())
    }

    @Test
    fun `M withTag(DATADOG_INITIAL_CONTEXT) W injectRumContextFeature`() {
        // Given
        val futureMock = completedFutureMock(fakeDatadogContext)
        mockRumFeatureScope = SdkFeatureMock.create(futureMock)

        // When
        mockSpanBuilder.injectRumContext(testedRumContextPropagator)

        // Then
        argumentCaptor<Future<DatadogContext>> {
            verify(mockSpanBuilder).withTag(eq(DATADOG_INITIAL_CONTEXT), capture())
            assertThat(firstValue).isEqualTo(futureMock)
        }
    }

    @Test
    fun `M set RUM tags W extractRumContext { DatadogSpan }`() {
        // Given
        val span = newDatadogSpanWithLazyDatadogContext(
            completedFutureMock(fakeDatadogContext)
        )

        // When
        span.extractRumContext(testedRumContextPropagator)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verify(span).setTag(LogAttributes.RUM_APPLICATION_ID, fakeRumContext[RUM_CONTEXT_APPLICATION_ID])
        verify(span).setTag(LogAttributes.RUM_SESSION_ID, fakeRumContext[RUM_CONTEXT_SESSION_ID])
        verify(span).setTag(LogAttributes.RUM_VIEW_ID, fakeRumContext[RUM_CONTEXT_VIEW_ID])
        verify(span).setTag(LogAttributes.RUM_ACTION_ID, fakeRumContext[RUM_CONTEXT_ACTION_ID])
        verify(span).setTag(HttpCodec.RUM_KEY_ACCOUNT_ID, fakeDatadogContext.accountInfo?.id as? Any)
        verify(span).setTag(HttpCodec.RUM_KEY_USER_ID, fakeDatadogContext.userInfo.id as? Any)
        verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)

        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M set RUM tags W extractRumContext { DDSpan }`() {
        // Given
        val span = newDDSpanWithLazyDatadogContext(
            completedFutureMock(fakeDatadogContext)
        )

        // When
        span.extractRumContext(testedRumContextPropagator)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verify(span).setTag(LogAttributes.RUM_APPLICATION_ID, fakeRumContext[RUM_CONTEXT_APPLICATION_ID])
        verify(span).setTag(LogAttributes.RUM_SESSION_ID, fakeRumContext[RUM_CONTEXT_SESSION_ID])
        verify(span).setTag(LogAttributes.RUM_VIEW_ID, fakeRumContext[RUM_CONTEXT_VIEW_ID])
        verify(span).setTag(LogAttributes.RUM_ACTION_ID, fakeRumContext[RUM_CONTEXT_ACTION_ID])
        verify(span).setTag(HttpCodec.RUM_KEY_ACCOUNT_ID, fakeDatadogContext.accountInfo?.id as? Any)
        verify(span).setTag(HttpCodec.RUM_KEY_USER_ID, fakeDatadogContext.userInfo.id as? Any)
        verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)

        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M set null RUM tags W extractRumContext { DatadogSpan, rum context is empty }`(forge: Forge) {
        // Given
        val futureMock = completedFutureMock(forge.aDatadogContextWithRumContext(emptyMap()))
        val span = newDatadogSpanWithLazyDatadogContext(futureMock)
        // When
        span.extractRumContext(testedRumContextPropagator)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verify(span).setTag(LogAttributes.RUM_APPLICATION_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_SESSION_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_VIEW_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_ACTION_ID, null as Any?)
        verify(span).setTag(HttpCodec.RUM_KEY_ACCOUNT_ID, null as Any?)
        verify(span).setTag(HttpCodec.RUM_KEY_USER_ID, null as Any?)
        verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)

        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M set null RUM tags W extractRumContext { DDSpan, rum context is empty }`(forge: Forge) {
        // Given
        val futureMock = completedFutureMock(forge.aDatadogContextWithRumContext(emptyMap()))
        val span = newDDSpanWithLazyDatadogContext(futureMock)

        // When
        span.extractRumContext(testedRumContextPropagator)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verify(span).setTag(LogAttributes.RUM_APPLICATION_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_SESSION_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_VIEW_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_ACTION_ID, null as Any?)
        verify(span).setTag(HttpCodec.RUM_KEY_ACCOUNT_ID, null as Any?)
        verify(span).setTag(HttpCodec.RUM_KEY_USER_ID, null as Any?)
        verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)

        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M not call setTag W getTag(DATADOG_INITIAL_CONTEXT) is null { DatadogSpan }`() {
        // Given
        val span = newDatadogSpanWithLazyDatadogContext(null)

        // When
        span.extractRumContext(testedRumContextPropagator)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M not call setTag W getTag(DATADOG_INITIAL_CONTEXT) is null { DDSpan }`() {
        // Given
        val span = newDDSpanWithLazyDatadogContext(null)

        // When
        span.extractRumContext(testedRumContextPropagator)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    companion object {
        fun newDDSpanWithLazyDatadogContext(value: Future<DatadogContext?>?) = mock<DDSpan> {
            on { getTag(DATADOG_INITIAL_CONTEXT) } doAnswer { value }
        }

        fun newDatadogSpanWithLazyDatadogContext(value: Future<DatadogContext?>?) =
            mock<DatadogSpan> {
                on { getTag(DATADOG_INITIAL_CONTEXT) } doAnswer { value }
            }

        fun InternalLogger.verifyErrorLogged(message: String, mode: VerificationMode = times(1)) = verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            message,
            mode = mode
        )

        fun verifyRumContextNotExtracted(span: Any) {
            when (span) {
                is DDSpan -> {
                    verify(span).getTag(DATADOG_INITIAL_CONTEXT)
                    verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)
                }

                is DatadogSpan -> {
                    verify(span).getTag(DATADOG_INITIAL_CONTEXT)
                    verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)
                }
            }
            verifyNoMoreInteractions(span)
        }
    }
}
