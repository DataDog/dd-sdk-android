/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.attributes

/**
 * A set of constants describing the instrumentation that were used to define the view scope.
 */
enum class ViewScopeInstrumentationType : LocalAttribute.Constant {
    /** Tracked manually through the RUMMonitor API. */
    MANUAL,

    /** Tracked through ComposeNavigationObserver instrumentation. */
    COMPOSE,

    /** Tracked through ActivityViewTrackingStrategy instrumentation. */
    ACTIVITY,

    /** Tracked through FragmentViewTrackingStrategy instrumentation. */
    FRAGMENT;

    /** @inheritdoc */
    override val key: LocalAttribute.Key = LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE
}
