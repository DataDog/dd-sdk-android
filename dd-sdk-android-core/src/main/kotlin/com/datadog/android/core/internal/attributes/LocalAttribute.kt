/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.core.internal.attributes

import com.datadog.android.lint.InternalApi

/**
 * Local attributes are used to pass additional metadata along with the event
 * and are never sent to the backend directly.
 */
interface LocalAttribute {

    enum class Key(val string: String) {
        /* Some of the metrics like [PerformanceMetric] being sampled on the
         * metric creation place and then reported with 100% probability.
         * In such cases we need to use *creationSampleRate* to compute effectiveSampleRate correctlyThe sampling
         * rate is used when creating metrics.
         * Creation(head) sampling rate exist only for long-lived metrics like method performance.
         * Created metric still could not be sent it depends on [REPORTING_SAMPLING_RATE_KEY] sampling rate
         */
        @InternalApi
        CREATION_SAMPLING_RATE("_dd.local.head_sampling_rate_key"),

        /* Sampling rate that is used to decide to send or not to send the metric.
         * Each metric should have reporting(tail) sampling rate.
         * It's possible that metric has only reporting(tail) sampling rate.
         */
        @InternalApi
        REPORTING_SAMPLING_RATE("_dd.local.tail_sampling_rate_key"),

        /*
         * Indicates which instrumentation was used to track the view scope.
         * See [ViewScopeInstrumentationType] for possible values
         */
        VIEW_SCOPE_INSTRUMENTATION_TYPE("_dd.local.view_instrumentation_type_key")
    }

    /**
     * Used for attributes that have a finite set of possible values (such as enumerations, see [ViewScopeInstrumentationType]).
     * This interface makes it possible to use only the value (see [enrichWithConstantAttribute]) when setting
     * an attribute and reduces the possibility of inconsistent use of api (when an unsupported value is passed
     * for a particular attribute key).
     */
    interface Constant {
        val key: Key
        val value: String
    }
}

fun MutableMap<String, Any?>.enrichWithConstantAttribute(
    attribute: LocalAttribute.Constant
) = enrichWithLocalAttribute(
    attribute.key,
    attribute.value
)

fun MutableMap<String, Any?>.enrichWithNonNullAttribute(
    key: LocalAttribute.Key,
    value: Any?
) = value?.let { enrichWithLocalAttribute(key, it) } ?: this

fun MutableMap<String, Any?>.enrichWithLocalAttribute(
    key: LocalAttribute.Key,
    value: Any?
) = apply {
    this[key.string] = value
}

enum class ViewScopeInstrumentationType(
    override val value: String
) : LocalAttribute.Constant {
    MANUAL("manual"),
    COMPOSE("compose"),
    ACTIVITY("activity"),
    FRAGMENT("fragment");

    override val key: LocalAttribute.Key = LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE
}
