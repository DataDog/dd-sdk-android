/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.webview

import android.webkit.WebView
import androidx.annotation.MainThread
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import java.util.concurrent.TimeUnit

internal class WebViewBrowserSnapshotHandler(
    private val rumContextProvider: RumContextProvider,
    private val dataPurgeTtlLimitInNs: Long = DATA_PURGE_TTL_LIMIT_IN_NS
) {

    internal val webViewsFullSnapshotState: MutableMap<Int, Pair<String, Long>> = mutableMapOf()

    @MainThread
    internal fun triggerFullSnapshotIfNeeded(webView: WebView) {
        val webViewId = System.identityHashCode(webView)
        val currentRumViewId = rumContextProvider.getRumContext().viewId
        val fullSnapshotInfo = webViewsFullSnapshotState[webViewId]
        if (fullSnapshotInfo?.first != currentRumViewId) {
            webViewsFullSnapshotState[webViewId] = Pair(currentRumViewId, System.nanoTime())
            webView.evaluateJavascript(FORCE_SNAPSHOT_JS_SIGNATURE, null)
        }
        purgeWebViewFullSnapshotStateMap()
    }

    private fun purgeWebViewFullSnapshotStateMap() {
        val iterator = webViewsFullSnapshotState.entries.iterator()
        @Suppress("UnsafeThirdPartyFunctionCall")
        // next/remove can't fail: we checked hasNext and there is no concurrent access
        // as method is called from the main thread only
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val timestamp = entry.value.second
            val timeSinceLastSnapshot = System.nanoTime() - timestamp
            if (timeSinceLastSnapshot > dataPurgeTtlLimitInNs) {
                iterator.remove()
            }
        }
        if (webViewsFullSnapshotState.size > DATA_CACHE_ENTRIES_LIMIT) {
            webViewsFullSnapshotState.entries
                .sortedBy { it.value.second }
                .take(webViewsFullSnapshotState.size - DATA_CACHE_ENTRIES_LIMIT)
                .forEach { webViewsFullSnapshotState.remove(it.key) }
        }
    }

    companion object {
        internal const val FORCE_SNAPSHOT_JS_SIGNATURE =
            "if(window != null && window.DD_RUM != null)" +
                "{window.DD_RUM.takeSessionReplayFullSnapshot()}"
        internal val DATA_PURGE_TTL_LIMIT_IN_NS = TimeUnit.MINUTES.toNanos(4)
        internal const val DATA_CACHE_ENTRIES_LIMIT = 10
    }
}
