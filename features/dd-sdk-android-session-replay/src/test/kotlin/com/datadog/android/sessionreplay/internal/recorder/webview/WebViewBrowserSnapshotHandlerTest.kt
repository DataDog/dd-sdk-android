/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.webview

import android.webkit.WebView
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class WebViewBrowserSnapshotHandlerTest {

    private lateinit var testedWebViewBrowserSnapshotHandler: WebViewBrowserSnapshotHandler

    @Mock
    lateinit var mockWebView: WebView

    @Mock
    lateinit var mockRumContextProvider: RumContextProvider

    @Forgery
    lateinit var fakeSessionReplayRumContext: SessionReplayRumContext

    @BeforeEach
    fun `set up`() {
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeSessionReplayRumContext)
        testedWebViewBrowserSnapshotHandler = WebViewBrowserSnapshotHandler(mockRumContextProvider)
    }

    @Test
    fun `M trigger a full snapshot W triggerFullSnapshotIfNeeded`() {
        // When
        testedWebViewBrowserSnapshotHandler.triggerFullSnapshotIfNeeded(mockWebView)

        // Then
        verify(mockWebView).evaluateJavascript(
            WebViewBrowserSnapshotHandler.FORCE_SNAPSHOT_JS_SIGNATURE,
            null
        )
        verifyNoMoreInteractions(mockWebView)
    }

    @Test
    fun `M trigger a full snapshot W triggerFullSnapshotIfNeeded{different web views}`() {
        // Given
        val mockNewWebView = mock<WebView>()

        // When
        testedWebViewBrowserSnapshotHandler.triggerFullSnapshotIfNeeded(mockWebView)
        testedWebViewBrowserSnapshotHandler.triggerFullSnapshotIfNeeded(mockNewWebView)

        // Then
        verify(mockWebView).evaluateJavascript(
            WebViewBrowserSnapshotHandler.FORCE_SNAPSHOT_JS_SIGNATURE,
            null
        )
        verify(mockNewWebView).evaluateJavascript(
            WebViewBrowserSnapshotHandler.FORCE_SNAPSHOT_JS_SIGNATURE,
            null
        )
        verifyNoMoreInteractions(mockWebView)
        verifyNoMoreInteractions(mockNewWebView)
    }

    @Test
    fun `M trigger a full snapshot only once W triggerFullSnapshotIfNeeded{same View id}`(
        forge: Forge
    ) {
        // Given
        val fakeNewRumContext = forge.getForgery<SessionReplayRumContext>()
            .copy(viewId = fakeSessionReplayRumContext.viewId)
        reset(mockRumContextProvider)
        whenever(mockRumContextProvider.getRumContext())
            .thenReturn(fakeSessionReplayRumContext)
            .thenReturn(fakeNewRumContext)
        testedWebViewBrowserSnapshotHandler.triggerFullSnapshotIfNeeded(mockWebView)

        // When
        testedWebViewBrowserSnapshotHandler.triggerFullSnapshotIfNeeded(mockWebView)

        // Then
        verify(mockWebView).evaluateJavascript(
            WebViewBrowserSnapshotHandler.FORCE_SNAPSHOT_JS_SIGNATURE,
            null
        )
        verifyNoMoreInteractions(mockWebView)
    }

    @Test
    fun `M trigger a new snapshot W triggerFullSnapshotIfNeeded{rum View changed}`(
        @Forgery fakeNewRumContext: SessionReplayRumContext
    ) {
        // Given
        reset(mockRumContextProvider)
        whenever(mockRumContextProvider.getRumContext())
            .thenReturn(fakeSessionReplayRumContext)
            .thenReturn(fakeNewRumContext)
        testedWebViewBrowserSnapshotHandler.triggerFullSnapshotIfNeeded(mockWebView)

        // When
        testedWebViewBrowserSnapshotHandler.triggerFullSnapshotIfNeeded(mockWebView)

        // Then
        verify(mockWebView, times(2)).evaluateJavascript(
            WebViewBrowserSnapshotHandler.FORCE_SNAPSHOT_JS_SIGNATURE,
            null
        )
        verifyNoMoreInteractions(mockWebView)
    }

    @Test
    fun `M purge the cached data W purgeWebViewFullSnapshotStateMap{ttl was exceeded}`() {
        // Given
        val ttlLimitInMs: Long = 500
        val ttlLimitInNs = TimeUnit.MILLISECONDS.toNanos(ttlLimitInMs)
        testedWebViewBrowserSnapshotHandler = WebViewBrowserSnapshotHandler(
            mockRumContextProvider,
            ttlLimitInNs
        )
        testedWebViewBrowserSnapshotHandler.triggerFullSnapshotIfNeeded(mockWebView)
        Thread.sleep(ttlLimitInMs)

        // When
        testedWebViewBrowserSnapshotHandler.triggerFullSnapshotIfNeeded(mockWebView)

        // Then
        assertThat(testedWebViewBrowserSnapshotHandler.webViewsFullSnapshotState).isEmpty()
    }

    @Test
    fun `M purge the cached data W purgeWebViewFullSnapshotStateMap{cache limit was exceeded}`(
        forge: Forge
    ) {
        // Given
        val cacheOverload = forge.anInt(
            min = WebViewBrowserSnapshotHandler.DATA_CACHE_ENTRIES_LIMIT,
            max = WebViewBrowserSnapshotHandler.DATA_CACHE_ENTRIES_LIMIT + 10
        )
        val numberOfInstances = WebViewBrowserSnapshotHandler.DATA_CACHE_ENTRIES_LIMIT +
            cacheOverload
        val fakeWebViews = forge.aList(size = numberOfInstances) { mock<WebView>() }
        val expectedCachedEntries = fakeWebViews
            .takeLast(WebViewBrowserSnapshotHandler.DATA_CACHE_ENTRIES_LIMIT)
            .map { System.identityHashCode(it) }

        // When
        fakeWebViews.forEach {
            testedWebViewBrowserSnapshotHandler.triggerFullSnapshotIfNeeded(it)
        }

        // Then
        assertThat(testedWebViewBrowserSnapshotHandler.webViewsFullSnapshotState.size)
            .isEqualTo(WebViewBrowserSnapshotHandler.DATA_CACHE_ENTRIES_LIMIT)
        assertThat(testedWebViewBrowserSnapshotHandler.webViewsFullSnapshotState.keys)
            .containsAll(expectedCachedEntries)
    }
}
