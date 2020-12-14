/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import android.os.Handler
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.domain.Time
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.scope.RumApplicationScope
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumScope
import com.datadog.android.rum.model.ViewEvent
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class DatadogRumMonitor(
    applicationId: UUID,
    internal val samplingRate: Float,
    private val writer: Writer<RumEvent>,
    internal val handler: Handler,
    firstPartyHostDetector: FirstPartyHostDetector
) : RumMonitor, AdvancedRumMonitor {

    internal val rootScope: RumScope = RumApplicationScope(
        applicationId,
        samplingRate,
        firstPartyHostDetector
    )

    internal val keepAliveRunnable = Runnable {
        handleEvent(RumRawEvent.KeepAlive())
    }

    init {
        handler.postDelayed(keepAliveRunnable, KEEP_ALIVE_MS)
    }

    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    // region RumMonitor

    override fun startView(key: Any, name: String, attributes: Map<String, Any?>) {
        handleEvent(
            RumRawEvent.StartView(key, name, attributes)
        )
    }

    override fun stopView(key: Any, attributes: Map<String, Any?>) {
        handleEvent(
            RumRawEvent.StopView(key, attributes)
        )
    }

    override fun addUserAction(type: RumActionType, name: String, attributes: Map<String, Any?>) {
        handleEvent(
            RumRawEvent.StartAction(type, name, false, attributes)
        )
    }

    override fun startUserAction(type: RumActionType, name: String, attributes: Map<String, Any?>) {
        handleEvent(
            RumRawEvent.StartAction(type, name, true, attributes)
        )
    }

    override fun stopUserAction(type: RumActionType, name: String, attributes: Map<String, Any?>) {
        handleEvent(
            RumRawEvent.StopAction(type, name, attributes)
        )
    }

    override fun startResource(
        key: String,
        method: String,
        url: String,
        attributes: Map<String, Any?>
    ) {
        handleEvent(
            RumRawEvent.StartResource(key, url, method, attributes)
        )
    }

    override fun stopResource(
        key: String,
        statusCode: Int?,
        size: Long?,
        kind: RumResourceKind,
        attributes: Map<String, Any?>
    ) {
        handleEvent(
            RumRawEvent.StopResource(key, statusCode?.toLong(), size, kind, attributes)
        )
    }

    override fun stopResourceWithError(
        key: String,
        statusCode: Int?,
        message: String,
        source: RumErrorSource,
        throwable: Throwable
    ) {
        handleEvent(
            RumRawEvent.StopResourceWithError(key, statusCode?.toLong(), message, source, throwable)
        )
    }

    override fun addError(
        message: String,
        source: RumErrorSource,
        throwable: Throwable?,
        attributes: Map<String, Any?>
    ) {
        handleEvent(
            RumRawEvent.AddError(message, source, throwable, null, false, attributes)
        )
    }

    override fun addErrorWithStacktrace(
        message: String,
        source: RumErrorSource,
        stacktrace: String?,
        attributes: Map<String, Any?>
    ) {
        handleEvent(
            RumRawEvent.AddError(message, source, null, stacktrace, false, attributes)
        )
    }

    // endregion

    // region AdvancedRumMonitor

    override fun resetSession() {
        handleEvent(
            RumRawEvent.ResetSession()
        )
    }

    override fun viewTreeChanged(eventTime: Time) {
        handleEvent(
            RumRawEvent.ViewTreeChanged(eventTime)
        )
    }

    override fun waitForResourceTiming(key: String) {
        handleEvent(
            RumRawEvent.WaitForResourceTiming(key)
        )
    }

    override fun addResourceTiming(key: String, timing: ResourceTiming) {
        handleEvent(
            RumRawEvent.AddResourceTiming(key, timing)
        )
    }

    override fun addCrash(message: String, source: RumErrorSource, throwable: Throwable) {
        handleEvent(
            RumRawEvent.AddError(message, source, throwable, null, true, emptyMap())
        )
    }

    override fun updateViewLoadingTime(
        key: Any,
        loadingTimeInNs: Long,
        type: ViewEvent.LoadingType
    ) {
        handleEvent(
            RumRawEvent.UpdateViewLoadingTime(key, loadingTimeInNs, type)
        )
    }

    override fun addTiming(name: String) {
        handleEvent(
            RumRawEvent.AddCustomTiming(name)
        )
    }

    // endregion

    // region Internal

    internal fun handleEvent(event: RumRawEvent) {
        handler.removeCallbacks(keepAliveRunnable)
        executorService.submit {
            synchronized(rootScope) {
                rootScope.handleEvent(event, writer)
            }
            handler.postDelayed(keepAliveRunnable, KEEP_ALIVE_MS)
        }
    }

    internal fun stopKeepAliveCallback() {
        handler.removeCallbacks(keepAliveRunnable)
    }

    // endregion

    companion object {
        internal val KEEP_ALIVE_MS = TimeUnit.MINUTES.toMillis(5)
    }
}
