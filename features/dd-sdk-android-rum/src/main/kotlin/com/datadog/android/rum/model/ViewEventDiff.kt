/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:DiffConfig(
    forClass = ViewEvent::class,
    ignore = ["date"],
    merge = ["application", "session", "view", "dd", "featureFlags"],
    visibility = DiffVisibility.INTERNAL,
)
@file:DiffConfig(
    forClass = ViewEvent.Application::class,
    ignore = ["id"],
    visibility = DiffVisibility.INTERNAL,
)
@file:DiffConfig(
    forClass = ViewEvent.ViewEventSession::class,
    ignore = ["id", "type"],
    visibility = DiffVisibility.INTERNAL,
)
@file:DiffConfig(
    forClass = ViewEvent.ViewEventView::class,
    ignore = ["id", "url"],
    append = ["slowFrames", "inForegroundPeriods"],
    merge = ["performance", "accessibility"],
    visibility = DiffVisibility.INTERNAL,
)
@file:DiffConfig(
    forClass = ViewEvent.Dd::class,
    ignore = ["documentVersion"],
    visibility = DiffVisibility.INTERNAL,
)
@file:DiffConfig(
    forClass = ViewEvent.Context::class,
    diffMap = ["additionalProperties"],
    visibility = DiffVisibility.INTERNAL,
)
package com.datadog.android.rum.model

import com.datadog.tools.diff.DiffConfig
import com.datadog.tools.diff.DiffVisibility

