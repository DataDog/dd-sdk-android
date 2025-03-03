/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric

internal data class ViewInitializationMetricsState(
    val initializationTime: Long?,
    val config: ViewInitializationMetricsConfig,
    val noValueReason: NoValueReason?
)

internal enum class ViewInitializationMetricsConfig {
    // Interaction to next view is disabled
    DISABLED,

    // The default time-based strategy is used.
    TIME_BASED_DEFAULT,

    // The default time-based strategy is used with a custom threshold.
    TIME_BASED_CUSTOM,

    // A custom strategy is used.
    CUSTOM
}

internal sealed interface NoValueReason {
    enum class TimeToNetworkSettle : NoValueReason {
        // When the value is missing because no resources were tracked in this view.
        NO_RESOURCES,

        // When some resources were tracked in this view, but none of them was classified as the "initial"
        // resource according to configured strategy.
        NO_INITIAL_RESOURCES,

        // When the view was stopped but not all the "initial" resources were loaded while it was active.
        NOT_SETTLED_YET,

        // For other reasons — this is used as a last resort (specific reasons are preferred).
        UNKNOWN
    }

    enum class InteractionToNextView : NoValueReason {
        // Interaction to next view is disabled, usually by a cross platform framework that wants
        // to handle this metric itself.
        DISABLED,

        // When the value is missing, because no action was tracked in previous view
        NO_ACTION,

        // When some actions were tracked in previous view,
        // but none of them was classified as the "last interaction" according to configured strategy.
        NO_ELIGIBLE_ACTION,

        // When the value is missing, because there was no previous view.
        NO_PREVIOUS_VIEW,

        // For other reasons — this is used as a last resort (specific reasons are preferred).
        UNKNOWN
    }
}
