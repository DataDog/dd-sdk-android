/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.fresco

import com.datadog.android.api.SdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.tools.unit.forge.aThrowable
import com.facebook.cache.common.CacheEvent
import com.facebook.imagepipeline.cache.BitmapMemoryCacheKey
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogFrescoCacheListenerTest {

    private lateinit var underTest: DatadogFrescoCacheListener

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockCacheEvent: CacheEvent

    lateinit var fakeEventKey: BitmapMemoryCacheKey

    @StringForgery(regex = "http://[a-z]+\\.com")
    lateinit var fakeCacheEventUri: String

    // region Unit Tests

    @BeforeEach
    fun `set up`() {
        GlobalRumMonitor::class.declaredFunctions.first { it.name == "registerIfAbsent" }.apply {
            isAccessible = true
            call(GlobalRumMonitor::class.objectInstance, mockRumMonitor, mockSdkCore)
        }
        fakeEventKey =
            BitmapMemoryCacheKey(
                fakeCacheEventUri,
                null,
                mock(),
                mock(),
                null,
                null,
                mock()
            )
        whenever(mockCacheEvent.cacheKey).thenReturn(fakeEventKey)
        underTest = DatadogFrescoCacheListener(mockSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor::class.java.getDeclaredMethod("reset").apply {
            isAccessible = true
            invoke(null)
        }
    }

    @Test
    fun `M add RUM Error event W onReadException()`(forge: Forge) {
        // GIVEN
        val fakeException: IOException? = forge.aNullable {
            IOException(forge.aThrowable())
        }
        whenever(mockCacheEvent.exception).doReturn(fakeException)

        // WHEN
        underTest.onReadException(mockCacheEvent)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(DatadogFrescoCacheListener.CACHE_ERROR_READ_MESSAGE),
            eq(RumErrorSource.SOURCE),
            eq(fakeException),
            argumentCaptor.capture()
        )
        assertThat(argumentCaptor.firstValue).containsEntry(
            DatadogFrescoCacheListener.CACHE_ENTRY_URI_TAG,
            fakeCacheEventUri
        )
    }

    @Test
    fun `M add RUM Error event W onReadException() with no throwable`() {
        // GIVEN
        whenever(mockCacheEvent.exception).doReturn(null)

        // WHEN
        underTest.onReadException(mockCacheEvent)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(DatadogFrescoCacheListener.CACHE_ERROR_READ_MESSAGE),
            eq(RumErrorSource.SOURCE),
            eq(null),
            argumentCaptor.capture()
        )
        assertThat(argumentCaptor.firstValue).containsEntry(
            DatadogFrescoCacheListener.CACHE_ENTRY_URI_TAG,
            fakeCacheEventUri
        )
    }

    @Test
    fun `M add RUM Error with empty tags W onReadException() with no CacheKey`(forge: Forge) {
        // GIVEN
        val fakeException: IOException? = forge.aNullable {
            IOException(forge.aThrowable())
        }
        whenever(mockCacheEvent.cacheKey).thenReturn(null)
        whenever(mockCacheEvent.exception).doReturn(fakeException)

        // WHEN
        underTest.onReadException(mockCacheEvent)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(DatadogFrescoCacheListener.CACHE_ERROR_READ_MESSAGE),
            eq(RumErrorSource.SOURCE),
            eq(fakeException),
            argumentCaptor.capture()
        )
        assertThat(argumentCaptor.firstValue).isEmpty()
    }

    @Test
    fun `M add RUM Error W onWriteException()`(forge: Forge) {
        // GIVEN
        val fakeException: IOException? = forge.aNullable {
            IOException(forge.aThrowable())
        }
        whenever(mockCacheEvent.exception).doReturn(fakeException)

        // WHEN
        underTest.onWriteException(mockCacheEvent)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(DatadogFrescoCacheListener.CACHE_ERROR_WRITE_MESSAGE),
            eq(RumErrorSource.SOURCE),
            eq(fakeException),
            argumentCaptor.capture()
        )
        assertThat(argumentCaptor.firstValue).containsEntry(
            DatadogFrescoCacheListener.CACHE_ENTRY_URI_TAG,
            fakeCacheEventUri
        )
    }

    @Test
    fun `M add RUM Error W onWriteException() with no Throwable`() {
        // GIVEN
        whenever(mockCacheEvent.exception).doReturn(null)

        // WHEN
        underTest.onWriteException(mockCacheEvent)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(DatadogFrescoCacheListener.CACHE_ERROR_WRITE_MESSAGE),
            eq(RumErrorSource.SOURCE),
            eq(null),
            argumentCaptor.capture()
        )
        assertThat(argumentCaptor.firstValue).containsEntry(
            DatadogFrescoCacheListener.CACHE_ENTRY_URI_TAG,
            fakeCacheEventUri
        )
    }

    @Test
    fun `M add RUM Error with empty tags W onWriteException() with no CacheKey`(forge: Forge) {
        // GIVEN
        val fakeException: IOException? = forge.aNullable {
            IOException(forge.aThrowable())
        }
        whenever(mockCacheEvent.cacheKey).thenReturn(null)
        whenever(mockCacheEvent.exception).doReturn(fakeException)

        // WHEN
        underTest.onWriteException(mockCacheEvent)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(DatadogFrescoCacheListener.CACHE_ERROR_WRITE_MESSAGE),
            eq(RumErrorSource.SOURCE),
            eq(fakeException),
            argumentCaptor.capture()
        )
        assertThat(argumentCaptor.firstValue).isEmpty()
    }

    // endregion
}
