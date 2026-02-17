/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.attributes

/**
 * Describes the instrumentation used to define a RUM view scope.
 *
 * Supports both native Android instrumentation types (Activity, Fragment, Compose, Manual)
 * and custom types from cross-platform SDKs (Flutter, React Native, Unity, etc.).
 */
sealed interface ViewScopeInstrumentationType : LocalAttribute.Constant {

    /**
     * The string value sent in telemetry for this instrumentation type.
     */
    val value: String

    /**
     * Native Android instrumentation types tracked by the SDK.
     */
    enum class Native(override val value: String) : ViewScopeInstrumentationType {
        /** Tracked manually through the RumMonitor API. */
        MANUAL("manual"),

        /** Tracked through ComposeNavigationObserver instrumentation. */
        COMPOSE("compose"),

        /** Tracked through ActivityViewTrackingStrategy instrumentation. */
        ACTIVITY("activity"),

        /** Tracked through FragmentViewTrackingStrategy instrumentation. */
        FRAGMENT("fragment");

        override val key: LocalAttribute.Key = LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE
    }

    /**
     * Custom instrumentation type for cross-platform SDKs.
     *
     * Used when cross-platform frameworks (Flutter, React Native, Unity, etc.)
     * need to report their specific navigation/instrumentation patterns.
     */
    class Custom internal constructor(private val customValue: String) : ViewScopeInstrumentationType {

        override val value: String get() = customValue

        override val key: LocalAttribute.Key get() = LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE

        companion object {
            /**
             * Creates a custom instrumentation type.
             *
             * @param type The custom type identifier
             */
            fun create(type: String): Custom {
                return Custom(type.trim())
            }
        }
    }
}
