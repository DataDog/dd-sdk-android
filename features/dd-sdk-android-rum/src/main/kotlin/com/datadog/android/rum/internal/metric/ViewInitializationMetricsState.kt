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
    TIME_BASED_DEFAULT,
    TIME_BASED_CUSTOM,
    CUSTOM
}

internal sealed interface NoValueReason {
    enum class TimeToNetworkSettle : NoValueReason {
        NO_RESOURCES,
        NO_INITIAL_RESOURCES,
        NOT_SETTLED_YET,
        UNKNOWN
    }

    enum class InteractionToNextView : NoValueReason {
        NO_ACTION,
        NO_ELIGIBLE_ACTION,
        NO_PREVIOUS_VIEW,
        UNKNOWN
    }
}
