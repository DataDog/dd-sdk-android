/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.scope.RumApplicationScope
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumScope
import java.util.UUID

internal class DatadogRumMonitor(
    applicationId: UUID,
    private val writer: Writer<RumEvent>
) : RumMonitor {

    private val rootScope: RumScope = RumApplicationScope(applicationId)

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

    override fun addUserAction(action: String, attributes: Map<String, Any?>) {
        handleEvent(
            RumRawEvent.StartAction(action, false, attributes)
        )
    }

    override fun startUserAction(action: String, attributes: Map<String, Any?>) {
        handleEvent(
            RumRawEvent.StartAction(action, true, attributes)
        )
    }

    override fun stopUserAction(action: String, attributes: Map<String, Any?>) {
        handleEvent(
            RumRawEvent.StopAction(action, attributes)
        )
    }

    override fun startResource(
        key: Any,
        method: String,
        url: String,
        attributes: Map<String, Any?>
    ) {
        handleEvent(
            RumRawEvent.StartResource(key, url, method, attributes)
        )
    }

    override fun stopResource(key: Any, kind: RumResourceKind, attributes: Map<String, Any?>) {
        handleEvent(
            RumRawEvent.StopResource(key, kind, attributes)
        )
    }

    override fun stopResourceWithError(
        key: Any,
        message: String,
        origin: String,
        throwable: Throwable
    ) {
        handleEvent(
            RumRawEvent.StopResourceWithError(key, message, origin, throwable)
        )
    }

    override fun addError(
        message: String,
        origin: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>
    ) {
        handleEvent(
            RumRawEvent.AddError(message, origin, throwable, attributes)
        )
    }

    // endregion

    // region Internal

    internal fun viewTreeChanged() {
        handleEvent(
            RumRawEvent.ViewTreeChanged()
        )
    }

    private fun handleEvent(event: RumRawEvent) {
        synchronized(rootScope) { rootScope.handleEvent(event, writer) }
    }

    // endregion
}
