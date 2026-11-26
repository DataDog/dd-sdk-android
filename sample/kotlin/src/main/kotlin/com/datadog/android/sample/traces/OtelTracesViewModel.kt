/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.traces

import androidx.lifecycle.ViewModel
import com.datadog.android.log.Logger
import com.datadog.android.okhttp.otel.addParentSpan
import com.datadog.android.sample.BuildConfig
import com.datadog.android.sample.sampleTaskDispatcher
import com.datadog.android.sample.sampleTaskScope
import com.datadog.android.vendor.sample.LocalServer
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import okhttp3.Request

internal class OtelTracesViewModel(
    private val okHttpClient: OkHttpClient,
    private val localServer: LocalServer
) : ViewModel() {

    private val taskScope = sampleTaskScope()

    private val asyncLogger by lazy { createLogger("async_task") }
    private val formLogger by lazy { createLogger("chained-contexts-task") }

    fun onResume() {
        localServer.start("https://www.datadoghq.com/")
    }

    fun onPause() {
        localServer.stop()
    }

    fun startAsyncOperation(onProgress: (Int) -> Unit = {}, onDone: () -> Unit = {}) {
        val tracer = GlobalOpenTelemetry.get().getTracer(OtelTracesViewModel::class.java.simpleName)
        val context = Context.current()
        taskScope.launch {
            tracer.spanBuilder("Executing Async Operation").setParent(context).startSpan().endAfter { parent ->
                val parentContext = context.with(parent)
                tracer.spanBuilder("OnPreExecute").setParent(parentContext).startSpan().end()
                runInterruptible(sampleTaskDispatcher) {
                    performAsyncOperation(tracer, parentContext) { progress ->
                        launch { onProgress(progress) }
                    }
                }
                parentContext.makeCurrent().use {
                    tracer.spanBuilder("OnPostExecute").startSpan().endAfter { onDone() }
                }
            }
        }
    }

    fun startChainedContexts(onDone: () -> Unit = {}) {
        val tracer = GlobalOpenTelemetry.get().getTracer("chainedContexts")
        val context = Context.current().with(emailKey, EMAIL).with(usernameKey, USERNAME)
        val url = localServer.getUrl()
        taskScope.launch {
            tracer.spanBuilder("submitForm with chained contexts").setParent(context).startSpan().endAfter { parent ->
                runInterruptible(sampleTaskDispatcher) {
                    submitForm(tracer, context.with(parent), url)
                }
            }
            onDone()
        }
    }

    fun startLinkedSpans(onDone: () -> Unit = {}) {
        val tracer = GlobalOpenTelemetry.get().getTracer("spanLinks")
        val context = Context.current()
        taskScope.launch {
            tracer.spanBuilder("submitForm with linked spans").setParent(context).startSpan().endAfter { parent ->
                runInterruptible(sampleTaskDispatcher) {
                    processLinkedSpans(tracer, context.with(parent))
                }
            }
            onDone()
        }
    }

    fun stopAsyncOperations() {
        taskScope.coroutineContext.cancelChildren()
    }

    private fun performAsyncOperation(tracer: Tracer, parentContext: Context, onProgress: (Int) -> Unit) {
        tracer.spanBuilder("AsyncOperation").setParent(parentContext).startSpan().endAfter {
            asyncLogger.v("Starting Async Operation...")
            for (progress in 0..MAX_PROGRESS) {
                onProgress(progress)
                Thread.sleep(traceProgressDelayMillis(progress))
            }
            asyncLogger.v("Finishing Async Operation...")
        }
    }

    private fun submitForm(tracer: Tracer, parentContext: Context, url: String) {
        tracer.spanBuilder("processingForm").setParent(parentContext).startSpan().endAfter { formSpan ->
            parentContext.with(formSpan).makeCurrent().use {
                tracer.spanBuilder("formSanitization").startSpan().endAfter {
                    formLogger.v("Sanitizing email: ${parentContext.get(emailKey)}")
                    formLogger.v("Sanitizing username: ${parentContext.get(usernameKey)}")
                    Thread.sleep(FORM_SANITIZATION_DELAY_MS)
                }
                val request = Request.Builder()
                    .get()
                    .url(url)
                    .addParentSpan(formSpan)
                    .build()
                okHttpClient.newCall(request).execute().close()
            }
        }
    }

    private fun processLinkedSpans(tracer: Tracer, parentContext: Context) {
        tracer.spanBuilder("processingForm").setParent(parentContext).startSpan().endAfter { formSpan ->
            parentContext.with(formSpan).makeCurrent().use {
                val attributes = Attributes.builder()
                    .put(EMAIL_ATTRIBUTE, EMAIL)
                    .put(USERNAME_ATTRIBUTE, USERNAME)
                    .build()
                tracer.spanBuilder("formSanitization")
                    .addLink(formSpan.spanContext, attributes)
                    .startSpan()
                    .endAfter {
                        formLogger.v("Sanitizing email")
                        formLogger.v("Sanitizing username")
                        Thread.sleep(FORM_SANITIZATION_DELAY_MS)
                    }
                Thread.sleep(LINKED_FORM_PROCESSING_DELAY_MS)
            }
        }
    }

    private fun createLogger(name: String): Logger = Logger.Builder()
        .setName(name)
        .setLogcatLogsEnabled(true)
        .build()
        .apply {
            addTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
            addTag(BUILD_TYPE, BuildConfig.BUILD_TYPE)
        }

    companion object {
        private const val BUILD_TYPE = "build_type"
        private const val ATTR_FLAVOR = "flavor"
        private const val EMAIL_ATTRIBUTE = "email"
        private const val USERNAME_ATTRIBUTE = "username"
        private const val EMAIL = "john.doe@example.com"
        private const val USERNAME = "John Doe"
        private const val MAX_PROGRESS = 100
        private const val FORM_SANITIZATION_DELAY_MS = 2_000L
        private const val LINKED_FORM_PROCESSING_DELAY_MS = 5_000L

        private val emailKey: ContextKey<String> = ContextKey.named(EMAIL_ATTRIBUTE)
        private val usernameKey: ContextKey<String> = ContextKey.named(USERNAME_ATTRIBUTE)
    }
}

private inline fun Span.endAfter(block: (Span) -> Unit) {
    try {
        block(this)
    } finally {
        end()
    }
}
