/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.domain.Time
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.UUID
import kotlin.math.max

internal class RumViewScope(
    private val parentScope: RumScope,
    key: Any,
    internal val name: String,
    eventTime: Time,
    initialAttributes: Map<String, Any?>,
    internal val firstPartyHostDetector: FirstPartyHostDetector
) : RumScope {

    internal val urlName = name.replace('.', '/')

    internal val keyRef: Reference<Any> = WeakReference(key)
    internal val attributes: MutableMap<String, Any?> = initialAttributes.toMutableMap()

    private var sessionId: String = parentScope.getRumContext().sessionId
    internal var viewId: String = UUID.randomUUID().toString()
        private set
    private val startedNanos: Long = eventTime.nanoTime

    internal val eventTimestamp = eventTime.timestamp

    internal var activeActionScope: RumScope? = null
    internal val activeResourceScopes = mutableMapOf<String, RumScope>()

    private var resourceCount: Long = 0
    private var actionCount: Long = 0
    private var errorCount: Long = 0
    private var crashCount: Long = 0
    private var version: Long = 1
    private var loadingTime: Long? = null
    private var loadingType: ViewEvent.LoadingType? = null
    private val customTimings: MutableMap<String, Long> = mutableMapOf()

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
            is RumRawEvent.ApplicationStarted -> onApplicationStarted(event, writer)
            is RumRawEvent.AddCustomTiming -> onAddCustomTiming(event, writer)
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
            stopped = true
            sendViewUpdate(event, writer)
            delegateEventToChildren(event, writer)
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
            stopped = true
            sendViewUpdate(event, writer)
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
        activeResourceScopes[event.key] = RumResourceScope.fromEvent(
            this,
            updatedEvent,
            firstPartyHostDetector
        )
    }

    private fun onAddError(
        event: RumRawEvent.AddError,
        writer: Writer<RumEvent>
    ) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        val context = getRumContext()
        val user = CoreFeature.userInfoProvider.getUserInfo()
        val updatedAttributes = addExtraAttributes(event.attributes)
        val networkInfo = CoreFeature.networkInfoProvider.getLatestNetworkInfo()
        val errorType = event.type ?: event.throwable?.javaClass?.canonicalName
        val errorEvent = ErrorEvent(
            date = event.eventTime.timestamp,
            error = ErrorEvent.Error(
                message = event.message,
                source = event.source.toSchemaSource(),
                stack = event.stacktrace ?: event.throwable?.loggableStackTrace(),
                isCrash = event.isFatal,
                type = errorType
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
            session = ErrorEvent.Session(
                id = context.sessionId,
                type = ErrorEvent.SessionType.USER
            ),
            dd = ErrorEvent.Dd()
        )
        val rumEvent = RumEvent(
            event = errorEvent,
            globalAttributes = updatedAttributes,
            userExtraAttributes = user.extraInfo
        )
        writer.write(rumEvent)
        errorCount++
        if (event.isFatal) {
            crashCount++
        }
        sendViewUpdate(event, writer)
    }

    private fun onAddCustomTiming(event: RumRawEvent.AddCustomTiming, writer: Writer<RumEvent>) {
        customTimings[event.name] = max(event.eventTime.nanoTime - startedNanos, 1L)
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
        val user = CoreFeature.userInfoProvider.getUserInfo()
        val timings = if (customTimings.isNotEmpty()) {
            ViewEvent.CustomTimings(LinkedHashMap(customTimings))
        } else {
            null
        }

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
                crash = ViewEvent.Crash(crashCount),
                customTimings = timings,
                isActive = !stopped
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
            globalAttributes = attributes,
            userExtraAttributes = user.extraInfo
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

    private fun onApplicationStarted(
        event: RumRawEvent.ApplicationStarted,
        writer: Writer<RumEvent>
    ) {
        val context = getRumContext()
        val user = CoreFeature.userInfoProvider.getUserInfo()

        val actionEvent = ActionEvent(
            date = eventTimestamp,
            action = ActionEvent.Action(
                type = ActionEvent.ActionType.APPLICATION_START,
                id = UUID.randomUUID().toString(),
                loadingTime = getStartupTime(event)
            ),
            view = ActionEvent.View(
                id = context.viewId.orEmpty(),
                url = context.viewUrl.orEmpty()
            ),
            usr = ActionEvent.Usr(
                id = user.id,
                name = user.name,
                email = user.email
            ),
            application = ActionEvent.Application(context.applicationId),
            session = ActionEvent.Session(
                id = context.sessionId,
                type = ActionEvent.SessionType.USER
            ),
            dd = ActionEvent.Dd()
        )
        val rumEvent = RumEvent(
            event = actionEvent,
            globalAttributes = GlobalRum.globalAttributes,
            userExtraAttributes = user.extraInfo
        )
        writer.write(rumEvent)

        actionCount++
        sendViewUpdate(event, writer)
    }

    private fun getStartupTime(event: RumRawEvent.ApplicationStarted): Long {
        val now = event.eventTime.nanoTime
        val startupTime = event.applicationStartupNanos
        return max(now - startupTime, 1L)
    }

    // endregion

    companion object {

        internal fun fromEvent(
            parentScope: RumScope,
            event: RumRawEvent.StartView,
            firstPartyHostDetector: FirstPartyHostDetector
        ): RumViewScope {
            return RumViewScope(
                parentScope,
                event.key,
                event.name,
                event.eventTime,
                event.attributes,
                firstPartyHostDetector
            )
        }
    }
}
