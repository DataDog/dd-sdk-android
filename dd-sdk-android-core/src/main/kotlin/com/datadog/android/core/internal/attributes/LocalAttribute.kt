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
@InternalApi
interface LocalAttribute {

    /**
     * Enumeration of all local attributes keys used in the application.
     * Made via **enum** to make sure that all such attributes will be removed before sending the event to the backend.
     *
     * @param string - Unique string value for a local attribute key.
     */
    @InternalApi
    enum class Key(
        private val string: String
    ) {
        /*
         * Some of the metrics such as [PerformanceMetric] are sampled at the point of
         * metric creation and then reported with 100% probability.
         * In such cases we need to use *creationSampleRate* to correctly calculate effectiveSampleRate.
         * The creation(head) sample rate only exists for long-lived metrics such as method performance.
         * Created metric still could not be sent, it depends on the [REPORTING_SAMPLING_RATE] sample rate.
         */
        CREATION_SAMPLING_RATE("_dd.local.head_sampling_rate_key"),

        /*
         * Sampling rate that is used to decide to send or not to send the metric.
         * Each metric should have reporting(tail) sampling rate.
         * It's possible that metric has only reporting(tail) sampling rate.
         */
        REPORTING_SAMPLING_RATE("_dd.local.tail_sampling_rate_key"),

        /*
         * Indicates which instrumentation was used to track the view scope.
         * See [ViewScopeInstrumentationType] for possible values.
         */
        VIEW_SCOPE_INSTRUMENTATION_TYPE("_dd.local.view_instrumentation_type_key");

        override fun toString(): String {
            return string
        }
    }

    /**
     * Used for attributes that have a finite set of possible values (such as enumerations, see [ViewScopeInstrumentationType]).
     * This interface makes it possible to use only the value (see [enrichWithConstantAttribute]) when setting
     * an attribute and reduces the possibility of inconsistent use of api (when an unsupported value is passed
     * for a particular attribute key).
     */
    @InternalApi
    interface Constant {
        /** Constant attribute key. For enum constants will be same for all values. */
        val key: Key
    }
}

/**
 * Adds local attribute to the mutable map.
 *
 * @param attribute - Constant attribute value that should be added.
 * Key for the attribute will be resolved automatically.
 */
@InternalApi
fun MutableMap<String, Any?>.enrichWithConstantAttribute(
    attribute: LocalAttribute.Constant
) = enrichWithLocalAttribute(
    attribute.key,
    attribute
)

/**
 * Adds value to the map for specified key if value is not null.
 *
 * @param key - local attribute key.
 * @param value - attribute value.
 */
@InternalApi
fun MutableMap<String, Any?>.enrichWithNonNullAttribute(
    key: LocalAttribute.Key,
    value: Any?
) = value?.let { enrichWithLocalAttribute(key, it) } ?: this

/**
 * Adds value to the map for specified key.
 *
 * @param key - local attribute key.
 * @param value - attribute value.
 */
@InternalApi
fun MutableMap<String, Any?>.enrichWithLocalAttribute(
    key: LocalAttribute.Key,
    value: Any?
) = apply {
    this[key.toString()] = value
}
