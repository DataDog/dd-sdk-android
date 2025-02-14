/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.core.internal.attributes

import com.datadog.android.lint.InternalApi

/**
 * A set of constants describing the instrumentation that were used to define the view scope.
 */
@InternalApi
enum class ViewScopeInstrumentationType(
    override val value: String
) : LocalAttribute.Constant {
    /** Tracked manually through the RUMMonitor API. */
    MANUAL("manual"),

    /** Tracked through ComposeNavigationObserver instrumentation. */
    COMPOSE("compose"),

    /** Tracked through ActivityViewTrackingStrategy instrumentation. */
    ACTIVITY("activity"),

    /** Tracked through FragmentViewTrackingStrategy instrumentation. */
    FRAGMENT("fragment");

    /** @inheritdoc */
    override val key: LocalAttribute.Key = LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE
}
