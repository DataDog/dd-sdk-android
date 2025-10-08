/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.getContextFuture
import com.datadog.android.lint.InternalApi
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.trace.core.DDSpan
import com.datadog.trace.core.propagation.HttpCodec
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * For internal usage only.
 *
 * This is a utility class that manages the propagation of RUM contexts for span events and HTTP tracing.
 */
@InternalApi
class RumContextPropagator(private val sdkCoreProvider: () -> FeatureSdkCore?) {

    private val sdkCore: FeatureSdkCore?
        get() = sdkCoreProvider()

    private val internalLogger: InternalLogger?
        get() = sdkCore?.internalLogger

    private fun injectRumContext(builder: DatadogSpanBuilder) {
        sdkCore?.getFeature(Feature.RUM_FEATURE_NAME)
            ?.getContextFuture(withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME))
            ?.let { lazyContext -> builder.withTag(DATADOG_INITIAL_CONTEXT, lazyContext) }
    }

    private fun extractRumContextInternal(instance: Any, block: Boolean = false) {
        val future = instance.getTag<Future<DatadogContext?>>(DATADOG_INITIAL_CONTEXT)

        if (future != null) {
            val datadogContext = when {
                block -> future.getOrNull(CONTEXT_RESOLUTION_TIMEOUT, TimeUnit.SECONDS)
                future.isDone -> future.getOrNull()
                else -> {
                    logError(INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR)
                    null
                }
            }

            if (datadogContext != null) {
                val rumContext = datadogContext
                    .featuresContext[Feature.RUM_FEATURE_NAME]
                    .orEmpty()

                instance.setTag(LogAttributes.RUM_APPLICATION_ID, rumContext["application_id"])
                instance.setTag(LogAttributes.RUM_SESSION_ID, rumContext["session_id"])
                instance.setTag(LogAttributes.RUM_VIEW_ID, rumContext["view_id"])
                instance.setTag(LogAttributes.RUM_ACTION_ID, rumContext["action_id"])
                instance.setTag(HttpCodec.RUM_KEY_USER_ID, datadogContext.userInfo.id)
                instance.setTag(HttpCodec.RUM_KEY_ACCOUNT_ID, datadogContext.accountInfo?.id)
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

    private fun <T> Future<T>.getOrNull(timeout: Long, unit: TimeUnit): T? = try {
        get(timeout, unit)
    } catch (_: TimeoutException) {
        logError(ERROR_DATADOG_CONTEXT_RESOLUTION_TIMEOUT)
        null
    } catch (_: Exception) {
        logError(ERROR_FUTURE_GET_FAILED)
        null
    }

    private fun <T> Future<T>.getOrNull(): T? = try {
        get()
    } catch (_: Exception) {
        logError(ERROR_FUTURE_GET_FAILED)
        null
    }

    private fun logError(message: String) {
        internalLogger?.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { message }
        )
    }

    companion object {
        internal const val DATADOG_INITIAL_CONTEXT: String = "_dd.datadog_initial_context"

        internal const val INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR = "Initial span creation Datadog context" +
            " is not available at the write time."

        internal const val ERROR_FUTURE_GET_FAILED = "Unable to get datadog context."
        internal const val ERROR_DATADOG_CONTEXT_RESOLUTION_TIMEOUT =
            "Datadog context resolution timeout exceeded."

        private const val CONTEXT_RESOLUTION_TIMEOUT = 1L

        /**
         * For internal usage only.
         *
         * Check for the Datadog context future and extracts the RUM context from it.
         * If RUM context is present - it will be added to the span.
         *
         * @param propagator the [RumContextPropagator] instance to use.
         * @param block if true, this method will block (1 second max) until the Datadog context is resolved.
         */
        @InternalApi
        fun DatadogSpan.extractRumContext(propagator: RumContextPropagator, block: Boolean = false) = apply {
            propagator.extractRumContextInternal(this, block)
        }

        internal fun DDSpan.extractRumContext(propagator: RumContextPropagator, block: Boolean = false) = apply {
            propagator.extractRumContextInternal(this, block)
        }

        internal fun DatadogSpanBuilder.injectRumContext(propagator: RumContextPropagator) = apply {
            propagator.injectRumContext(this)
        }
    }
}
