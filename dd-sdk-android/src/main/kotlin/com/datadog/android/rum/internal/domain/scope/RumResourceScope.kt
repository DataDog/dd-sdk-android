/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.domain.Time
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.model.ErrorEvent
import com.datadog.android.rum.internal.domain.model.ResourceEvent

internal class RumResourceScope(
    val parentScope: RumScope,
    val url: String,
    val method: String,
    val key: String,
    eventTime: Time,
    initialAttributes: Map<String, Any?>
) : RumScope {

    val attributes: MutableMap<String, Any?> = initialAttributes.toMutableMap()
    var timing: ResourceTiming? = null

    internal val eventTimestamp = eventTime.timestamp
    internal val startedNanos: Long = eventTime.nanoTime
    private val networkInfo = RumFeature.networkInfoProvider.getLatestNetworkInfo()

    private var sent = false
    private var waitForTiming = false
    private var stopped = false
    private var kind: RumResourceKind = RumResourceKind.UNKNOWN
    private var statusCode: Long? = null
    private var size: Long? = null

    // region RumScope

    override fun handleEvent(event: RumRawEvent, writer: Writer<RumEvent>): RumScope? {
        when (event) {
            is RumRawEvent.WaitForResourceTiming -> if (key == event.key) waitForTiming = true
            is RumRawEvent.AddResourceTiming -> onAddResourceTiming(event, writer)
            is RumRawEvent.StopResource -> onStopResource(event, writer)
            is RumRawEvent.StopResourceWithError -> onStopResourceWithError(event, writer)
        }
        return if (sent) null else this
    }

    override fun getRumContext(): RumContext {
        return parentScope.getRumContext()
    }

    // endregion

    // region  Internal

    private fun onStopResource(
        event: RumRawEvent.StopResource?,
        writer: Writer<RumEvent>
    ) {
        if (key != event?.key) return

        stopped = true
        attributes.putAll(event.attributes)
        kind = event.kind
        statusCode = event.statusCode
        size = event.size

        if (!(waitForTiming && timing == null)) {
            sendResource(kind, event.statusCode, event.size, event.eventTime, writer)
        }
    }

    private fun onAddResourceTiming(
        event: RumRawEvent.AddResourceTiming,
        writer: Writer<RumEvent>
    ) {
        if (key != event.key) return

        timing = event.timing
        if (stopped && !sent) {
            sendResource(kind, statusCode, size, event.eventTime, writer)
        }
    }

    private fun onStopResourceWithError(
        event: RumRawEvent.StopResourceWithError,
        writer: Writer<RumEvent>
    ) {
        if (key != event.key) return

        sendError(
            event.message,
            event.source,
            event.statusCode,
            event.throwable,
            writer
        )
    }

    private fun sendResource(
        kind: RumResourceKind,
        statusCode: Long?,
        size: Long?,
        eventTime: Time,
        writer: Writer<RumEvent>
    ) {
        attributes.putAll(GlobalRum.globalAttributes)

        val context = getRumContext()
        val user = RumFeature.userInfoProvider.getUserInfo()
        val networkInfo = RumFeature.networkInfoProvider.getLatestNetworkInfo()

        val resourceEvent = ResourceEvent(
            date = eventTimestamp,
            resource = ResourceEvent.Resource(
                type = kind.toSchemaType(),
                url = url,
                duration = eventTime.nanoTime - startedNanos,
                method = method.toMethod(),
                statusCode = statusCode,
                size = size,
                dns = timing?.dns(),
                connect = timing?.connect(),
                ssl = timing?.ssl(),
                firstByte = timing?.firstByte(),
                download = timing?.download()
            ),
            action = context.actionId?.let { ResourceEvent.Action(it) },
            view = ResourceEvent.View(
                id = context.viewId.orEmpty(),
                url = context.viewUrl.orEmpty()
            ),
            usr = ResourceEvent.Usr(
                id = user.id,
                name = user.name,
                email = user.email
            ),
            connectivity = networkInfo.toResourceConnectivity(),
            application = ResourceEvent.Application(context.applicationId),
            session = ResourceEvent.Session(
                id = context.sessionId,
                type = ResourceEvent.Type.USER
            ),
            dd = ResourceEvent.Dd()
        )
        val rumEvent = RumEvent(
            event = resourceEvent,
            attributes = attributes
        )
        writer.write(rumEvent)
        parentScope.handleEvent(RumRawEvent.SentResource(), writer)
        sent = true
    }

    private fun sendError(
        message: String,
        source: RumErrorSource,
        statusCode: Long?,
        throwable: Throwable,
        writer: Writer<RumEvent>
    ) {
        attributes.putAll(GlobalRum.globalAttributes)

        val context = getRumContext()
        val user = RumFeature.userInfoProvider.getUserInfo()
        val networkInfo = RumFeature.networkInfoProvider.getLatestNetworkInfo()

        val errorEvent = ErrorEvent(
            date = eventTimestamp,
            error = ErrorEvent.Error(
                message = message,
                source = source.toSchemaSource(),
                stack = throwable.loggableStackTrace(),
                isCrash = false,
                resource = ErrorEvent.Resource(
                    url = url,
                    method = method.toErrorMethod(),
                    statusCode = statusCode ?: 0
                )
            ),
            action = context.actionId?.let { ErrorEvent.Action(it) },
            view = ErrorEvent.View(
                id = context.viewId.orEmpty(),
                url = context.viewUrl.orEmpty()
            ),
            usr = ErrorEvent.Usr(
                id = user.id,
                name = user.name,
                email = user.email
            ),
            connectivity = networkInfo.toErrorConnectivity(),
            application = ErrorEvent.Application(context.applicationId),
            session = ErrorEvent.Session(id = context.sessionId, type = ErrorEvent.Type.USER),
            dd = ErrorEvent.Dd()
        )
        val rumEvent = RumEvent(
            event = errorEvent,
            attributes = attributes
        )
        writer.write(rumEvent)
        parentScope.handleEvent(RumRawEvent.SentError(), writer)
        sent = true
    }

    // endregion

    companion object {
        fun fromEvent(
            parentScope: RumScope,
            event: RumRawEvent.StartResource
        ): RumScope {
            return RumResourceScope(
                parentScope,
                event.url,
                event.method,
                event.key,
                event.eventTime,
                event.attributes
            )
        }
    }
}
