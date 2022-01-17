/*
 * Unless explicitly stated otherwise all files in event repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent

internal class WebViewRumEventMapper {

    fun mapViewEvent(
        event: ViewEvent,
        context: RumContext,
        timeOffset: Long
    ): ViewEvent {

        return event.copy(
            application = event.application.copy(id = context.applicationId),
            session = event.session.copy(id = context.sessionId),
            date = event.date + timeOffset,
            dd = event.dd.copy(session = ViewEvent.DdSession(ViewEvent.Plan.PLAN_1))
        )
    }

    fun mapActionEvent(
        event: ActionEvent,
        context: RumContext,
        timeOffset: Long
    ): ActionEvent {

        return event.copy(
            application = event.application.copy(id = context.applicationId),
            session = event.session.copy(id = context.sessionId),
            date = event.date + timeOffset,
            dd = event.dd.copy(session = ActionEvent.DdSession(ActionEvent.Plan.PLAN_1))
        )
    }

    fun mapResourceEvent(
        event: ResourceEvent,
        context: RumContext,
        timeOffset: Long
    ): ResourceEvent {
        return event.copy(
            application = event.application.copy(id = context.applicationId),
            session = event.session.copy(id = context.sessionId),
            date = event.date + timeOffset,
            dd = event.dd.copy(
                session = ResourceEvent.DdSession(plan = ResourceEvent.Plan.PLAN_1)
            )
        )
    }

    fun mapLongTaskEvent(
        event: LongTaskEvent,
        context: RumContext,
        timeOffset: Long
    ): LongTaskEvent {
        return event.copy(
            application = event.application.copy(id = context.applicationId),
            session = event.session.copy(id = context.sessionId),
            date = event.date + timeOffset,
            dd = event.dd.copy(session = LongTaskEvent.DdSession(LongTaskEvent.Plan.PLAN_1))

        )
    }

    fun mapErrorEvent(
        event: ErrorEvent,
        context: RumContext,
        timeOffset: Long
    ): ErrorEvent {
        return event.copy(
            application = event.application.copy(id = context.applicationId),
            session = event.session.copy(id = context.sessionId),
            date = event.date + timeOffset,
            dd = event.dd.copy(session = ErrorEvent.DdSession(ErrorEvent.Plan.PLAN_1))
        )
    }
}
