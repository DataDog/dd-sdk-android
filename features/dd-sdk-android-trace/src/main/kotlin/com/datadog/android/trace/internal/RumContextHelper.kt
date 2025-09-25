/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.internal.concurrent.CompletableFuture
import com.datadog.android.lint.InternalApi
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.trace.core.DDSpan

/**
 * For internal usage only.
 *
 * Utility object that manage RUM context propagation for span events and http tracing
 */
@InternalApi
object RumContextHelper {
    /**
     * A constant used to store RUM context future.
     */
    const val DATADOG_INITIAL_CONTEXT: String = "_dd.datadog_initial_context"
    internal const val INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR = "Initial span creation Datadog context" +
        " is not available at the write time."

    /**
     * Helper function that adds RUM context future to the tags for lazy context.
     */
    fun DatadogSpanBuilder.injectRumContextFeature(rumFeature: FeatureScope) = apply {
        val lazyContext = CompletableFuture<DatadogContext>()
        rumFeature.withContext(withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)) { lazyContext.complete(it) }
        withTag(DATADOG_INITIAL_CONTEXT, lazyContext)
    }

    /**
     * Helper function that extracts RUM context from the future.
     */
    fun DatadogSpan.extractRumContextFeature(internalLogger: InternalLogger) = apply {
        extractRumContextFeature(this, internalLogger)
    }

    internal fun DDSpan.extractRumContextFeature(internalLogger: InternalLogger) = apply {
        extractRumContextFeature(this, internalLogger)
    }

    private fun extractRumContextFeature(instance: Any, internalLogger: InternalLogger) {
        val initialDatadogContext = instance.getTag<CompletableFuture<DatadogContext>>(DATADOG_INITIAL_CONTEXT)

        if (initialDatadogContext != null) {
            if (initialDatadogContext.isComplete()) {
                val rumContext = initialDatadogContext.value
                    .featuresContext[Feature.RUM_FEATURE_NAME]
                    .orEmpty()

                instance.setTag(LogAttributes.RUM_APPLICATION_ID, rumContext["application_id"])
                instance.setTag(LogAttributes.RUM_SESSION_ID, rumContext["session_id"])
                instance.setTag(LogAttributes.RUM_VIEW_ID, rumContext["view_id"])
                instance.setTag(LogAttributes.RUM_ACTION_ID, rumContext["action_id"])
            } else {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.USER, InternalLogger.Target.MAINTAINER),
                    { INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR }
                )
            }
            instance.setTag(DATADOG_INITIAL_CONTEXT, null)
        }
    }

    private fun Any.setTag(key: String, value: Any?) {
        when (this) {
            is DDSpan -> setTag(key, value)
            is DatadogSpan -> setTag(key, value)
        }
    }

    private inline fun <reified T> Any.getTag(key: String): T? = when (this) {
        is DDSpan -> getTag(key) as? T
        is DatadogSpan -> getTag(key) as? T
        else -> null
    }
}
