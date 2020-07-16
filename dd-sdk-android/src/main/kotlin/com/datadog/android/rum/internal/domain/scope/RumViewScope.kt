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
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.model.ErrorEvent
import com.datadog.android.rum.internal.domain.model.ViewEvent
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.UUID

internal class RumViewScope(
    internal val parentScope: RumScope,
    key: Any,
    internal val name: String,
    eventTime: Time,
    initialAttributes: Map<String, Any?>
) : RumScope {

    internal val urlName = name.replace('.', '/')

    internal val keyRef: Reference<Any> = WeakReference(key)
    internal val attributes: MutableMap<String, Any?> = initialAttributes.toMutableMap()

    internal var sessionId: String = parentScope.getRumContext().sessionId
    internal var viewId: String = UUID.randomUUID().toString()
    internal val startedNanos: Long = eventTime.nanoTime

    internal val eventTimestamp = eventTime.timestamp

    internal var activeActionScope: RumScope? = null
    internal val activeResourceScopes = mutableMapOf<String, RumScope>()

    internal var resourceCount: Long = 0
    internal var actionCount: Long = 0
    internal var errorCount: Long = 0
    internal var crashCount: Long = 0
    internal var version: Long = 1
    internal var loadingTime: Long? = null
    internal var loadingType: ViewEvent.LoadingType? = null

    internal var stopped: Boolean = false

    init {
        GlobalRum.updateRumContext(getRumContext())
        attributes.putAll(GlobalRum.globalAttributes)
    }

    // region RumScope

    override fun handleEvent(
        event: RumRawEvent,
        writer: Writer<RumEvent>
    ): RumScope? {

        when (event) {
            is RumRawEvent.SentError -> {
                errorCount++
                sendViewUpdate(event, writer)
            }
            is RumRawEvent.SentResource -> {
                resourceCount++
                sendViewUpdate(event, writer)
            }
            is RumRawEvent.SentAction -> {
                actionCount++
                sendViewUpdate(event, writer)
            }
            is RumRawEvent.StartView -> onStartView(event, writer)
            is RumRawEvent.StopView -> onStopView(event, writer)
            is RumRawEvent.StartAction -> onStartAction(event, writer)
            is RumRawEvent.StartResource -> onStartResource(event, writer)
            is RumRawEvent.AddError -> onAddError(event, writer)
            is RumRawEvent.KeepAlive -> onKeepAlive(event, writer)
            is RumRawEvent.UpdateViewLoadingTime -> onUpdateViewLoadingTime(event, writer)
            else -> delegateEventToChildren(event, writer)
        }

        return if (stopped && activeResourceScopes.isEmpty()) {
            null
        } else {
            this
        }
    }

    override fun getRumContext(): RumContext {
        val parentContext = parentScope.getRumContext()
        if (parentContext.sessionId != sessionId) {
            sessionId = parentContext.sessionId
            viewId = UUID.randomUUID().toString()
        }

        return parentContext
            .copy(
                viewId = viewId,
                viewUrl = urlName,
                actionId = (activeActionScope as? RumActionScope)?.actionId
            )
    }

    // endregion

    // region Internal

    private fun onStartView(
        event: RumRawEvent.StartView,
        writer: Writer<RumEvent>
    ) {
        if (!stopped) {
            sendViewUpdate(event, writer)
            delegateEventToChildren(event, writer)
            stopped = true
        }
    }

    private fun onStopView(
        event: RumRawEvent.StopView,
        writer: Writer<RumEvent>
    ) {
        delegateEventToChildren(event, writer)
        val startedKey = keyRef.get()
        val shouldStop = (event.key == startedKey) || (startedKey == null)
        if (shouldStop && !stopped) {
            attributes.putAll(event.attributes)
            sendViewUpdate(event, writer)
            stopped = true
        }
    }

    private fun onStartAction(
        event: RumRawEvent.StartAction,
        writer: Writer<RumEvent>
    ) {
        delegateEventToChildren(event, writer)

        if (stopped || activeActionScope != null) return

        activeActionScope = RumActionScope.fromEvent(this, event)
    }

    private fun onStartResource(
        event: RumRawEvent.StartResource,
        writer: Writer<RumEvent>
    ) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        val updatedEvent = event.copy(
            attributes = addExtraAttributes(event.attributes)
        )
        activeResourceScopes[event.key] = RumResourceScope.fromEvent(this, updatedEvent)
    }

    private fun onAddError(
        event: RumRawEvent.AddError,
        writer: Writer<RumEvent>
    ) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        val context = getRumContext()
        val user = RumFeature.userInfoProvider.getUserInfo()
        val updatedAttributes = addExtraAttributes(event.attributes)
        val networkInfo = RumFeature.networkInfoProvider.getLatestNetworkInfo()

        val errorEvent = ErrorEvent(
            date = eventTimestamp,
            error = ErrorEvent.Error(
                message = event.message,
                source = event.source.toSchemaSource(),
                stack = event.throwable?.loggableStackTrace(),
                isCrash = event.isFatal
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
            attributes = updatedAttributes
        )
        writer.write(rumEvent)
        errorCount++
        if (event.isFatal) {
            crashCount++
        }
        sendViewUpdate(event, writer)
    }

    private fun onKeepAlive(
        event: RumRawEvent.KeepAlive,
        writer: Writer<RumEvent>
    ) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        sendViewUpdate(event, writer)
    }

    private fun delegateEventToChildren(
        event: RumRawEvent,
        writer: Writer<RumEvent>
    ) {
        delegateEventToResources(event, writer)
        delegateEventToAction(event, writer)
    }

    private fun delegateEventToAction(
        event: RumRawEvent,
        writer: Writer<RumEvent>
    ) {
        val currentAction = activeActionScope
        if (currentAction != null) {
            val updatedAction = currentAction.handleEvent(event, writer)
            if (updatedAction == null) {
                activeActionScope = null
            }
        }
    }

    private fun delegateEventToResources(
        event: RumRawEvent,
        writer: Writer<RumEvent>
    ) {
        val iterator = activeResourceScopes.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val scope = entry.value.handleEvent(event, writer)
            if (scope == null) {
                iterator.remove()
            }
        }
    }

    private fun sendViewUpdate(event: RumRawEvent, writer: Writer<RumEvent>) {
        attributes.putAll(GlobalRum.globalAttributes)
        version++
        val updatedDurationNs = event.eventTime.nanoTime - startedNanos
        val context = getRumContext()
        val user = RumFeature.userInfoProvider.getUserInfo()

        val viewEvent = ViewEvent(
            date = eventTimestamp,
            view = ViewEvent.View(
                id = context.viewId.orEmpty(),
                url = context.viewUrl.orEmpty(),
                loadingTime = loadingTime,
                loadingType = loadingType,
                timeSpent = updatedDurationNs,
                action = ViewEvent.Action(actionCount),
                resource = ViewEvent.Resource(resourceCount),
                error = ViewEvent.Error(errorCount),
                crash = ViewEvent.Crash(crashCount)
            ),
            usr = ViewEvent.Usr(
                id = user.id,
                name = user.name,
                email = user.email
            ),
            application = ViewEvent.Application(context.applicationId),
            session = ViewEvent.Session(id = context.sessionId, type = ViewEvent.Type.USER),
            dd = ViewEvent.Dd(documentVersion = version)
        )

        val rumEvent = RumEvent(
            event = viewEvent,
            attributes = attributes
        )
        writer.write(rumEvent)
    }

    private fun addExtraAttributes(
        attributes: Map<String, Any?>
    ): MutableMap<String, Any?> {
        return attributes.toMutableMap()
            .apply { putAll(GlobalRum.globalAttributes) }
    }

    private fun onUpdateViewLoadingTime(
        event: RumRawEvent.UpdateViewLoadingTime,
        writer: Writer<RumEvent>
    ) {
        val startedKey = keyRef.get()
        if (event.key != startedKey) {
            return
        }
        loadingTime = event.loadingTime
        loadingType = event.loadingType
        sendViewUpdate(event, writer)
    }

    // endregion

    companion object {

        internal fun fromEvent(
            parentScope: RumScope,
            event: RumRawEvent.StartView
        ): RumViewScope {
            return RumViewScope(
                parentScope,
                event.key,
                event.name,
                event.eventTime,
                event.attributes
            )
        }
    }
}
