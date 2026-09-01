/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.traces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datadog.android.log.Logger
import com.datadog.android.okhttp.otel.addParentSpan
import com.datadog.android.sample.BuildConfig
import com.datadog.android.vendor.sample.LocalServer
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class OtelTracesViewModel(
    private val okHttpClient: OkHttpClient,
    private val localServer: LocalServer
) : ViewModel() {

    private var asyncOperationJob: Job? = null
    private var chainedContextsJob: Job? = null
    private var linkedSpansJob: Job? = null

    private val asyncOperationLogger: Logger by lazy {
        buildLogger("async_operation")
    }
    private val chainedContextsLogger: Logger by lazy {
        buildLogger("chained-contexts-task")
    }

    fun onResume() {
        localServer.start("https://www.datadoghq.com/")
    }

    fun onPause() {
        localServer.stop()
    }

    fun startAsyncOperation(
        onProgress: (Int) -> Unit = {},
        onDone: () -> Unit = {}
    ) {
        asyncOperationJob?.cancel()
        val parentContext = Context.current()
        asyncOperationJob = viewModelScope.launch {
            runAsyncOperation(parentContext, onProgress, onDone)
        }
    }

    fun startChainedContexts(onDone: () -> Unit = {}) {
        chainedContextsJob?.cancel()
        val parentContext = Context.current()
        val url = localServer.getUrl()
        chainedContextsJob = viewModelScope.launch {
            runChainedContexts(parentContext, url, onDone)
        }
    }

    fun startLinkedSpans(onDone: () -> Unit = {}) {
        linkedSpansJob?.cancel()
        val parentContext = Context.current()
        linkedSpansJob = viewModelScope.launch {
            runLinkedSpans(parentContext, onDone)
        }
    }

    fun stopAsyncOperations() {
        asyncOperationJob?.cancel()
        chainedContextsJob?.cancel()
        linkedSpansJob?.cancel()
        asyncOperationJob = null
        chainedContextsJob = null
        linkedSpansJob = null
    }

    @Suppress("MagicNumber")
    private suspend fun runAsyncOperation(
        parentContext: Context,
        onProgress: (Int) -> Unit,
        onDone: () -> Unit
    ) {
        val tracer = GlobalOpenTelemetry.get()
            .getTracer(OtelTracesViewModel::class.java.simpleName)
        val parentSpan = tracer
            .spanBuilder("Executing Async Operation")
            .setParent(parentContext)
            .startSpan()
        val operationContext = parentContext.with(parentSpan)

        try {
            tracer.spanBuilder("OnPreExecute")
                .setParent(operationContext)
                .startSpan()
                .end()

            withContext(Dispatchers.Default) {
                val asyncOperationSpan = tracer
                    .spanBuilder("AsyncOperation")
                    .setParent(operationContext)
                    .startSpan()
                try {
                    asyncOperationLogger.v("Starting Async Operation...")
                    for (progress in 0..100) {
                        ensureActive()
                        withContext(Dispatchers.Main.immediate) {
                            onProgress(progress)
                        }
                        delay(((progress * progress).toDouble() / 100.0).toLong())
                    }
                    asyncOperationLogger.v("Finishing Async Operation...")
                } finally {
                    asyncOperationSpan.end()
                }
            }

            val postExecuteSpan = tracer
                .spanBuilder("OnPostExecute")
                .setParent(operationContext)
                .startSpan()
            try {
                onDone()
            } finally {
                postExecuteSpan.end()
            }
        } finally {
            parentSpan.end()
        }
    }

    @Suppress("MagicNumber")
    private suspend fun runChainedContexts(
        parentContext: Context,
        url: String,
        onDone: () -> Unit
    ) {
        val tracer = GlobalOpenTelemetry.get().getTracer("chainedContexts")
        val emailKey: ContextKey<String> = ContextKey.named("email")
        val usernameKey: ContextKey<String> = ContextKey.named("username")
        val context = parentContext
            .with(emailKey, "john.doe@example.com")
            .with(usernameKey, "John Doe")
        val startSpan = tracer
            .spanBuilder("submitForm with chained contexts")
            .setParent(context)
            .startSpan()
        val startContext = context.with(startSpan)

        try {
            withContext(Dispatchers.IO) {
                val processingFormSpan = tracer
                    .spanBuilder("processingForm")
                    .setParent(startContext)
                    .startSpan()
                try {
                    sanitizeForm(
                        tracer = tracer,
                        parentContext = startContext.with(processingFormSpan),
                        email = context.get(emailKey),
                        username = context.get(usernameKey)
                    )
                    val request = Request.Builder()
                        .get()
                        .url(url)
                        .addParentSpan(processingFormSpan)
                        .build()
                    okHttpClient.newCall(request).execute().use {
                        // Closing the response releases the connection.
                    }
                } finally {
                    processingFormSpan.end()
                }
            }
            onDone()
        } finally {
            startSpan.end()
        }
    }

    @Suppress("MagicNumber")
    private suspend fun sanitizeForm(
        tracer: Tracer,
        parentContext: Context,
        email: String?,
        username: String?
    ) {
        val sanitizationSpan = tracer
            .spanBuilder("formSanitization")
            .setParent(parentContext)
            .startSpan()
        try {
            chainedContextsLogger.v("Sanitizing email: $email")
            chainedContextsLogger.v("Sanitizing username: $username")
            delay(2000)
        } finally {
            sanitizationSpan.end()
        }
    }

    @Suppress("MagicNumber")
    private suspend fun runLinkedSpans(
        parentContext: Context,
        onDone: () -> Unit
    ) {
        val tracer = GlobalOpenTelemetry.get().getTracer("spanLinks")
        val startSpan = tracer
            .spanBuilder("submitForm with linked spans")
            .setParent(parentContext)
            .startSpan()
        val startContext = parentContext.with(startSpan)

        try {
            withContext(Dispatchers.Default) {
                val processingFormSpan = tracer
                    .spanBuilder("processingForm")
                    .setParent(startContext)
                    .startSpan()
                try {
                    val attributes = Attributes.builder()
                        .put("email", "john.doe@example.com")
                        .put("username", "John Doe")
                        .build()
                    val sanitizationSpan = tracer
                        .spanBuilder("formSanitization")
                        .setParent(startContext.with(processingFormSpan))
                        .addLink(processingFormSpan.spanContext, attributes)
                        .startSpan()
                    try {
                        chainedContextsLogger.v("Sanitizing email")
                        chainedContextsLogger.v("Sanitizing username")
                        delay(2000)
                    } finally {
                        sanitizationSpan.end()
                    }
                    delay(5000)
                } finally {
                    processingFormSpan.end()
                }
            }
            onDone()
        } finally {
            startSpan.end()
        }
    }

    private fun buildLogger(name: String): Logger {
        return Logger.Builder()
            .setName(name)
            .setLogcatLogsEnabled(true)
            .build()
            .apply {
                addTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
                addTag(BUILD_TYPE, BuildConfig.BUILD_TYPE)
            }
    }

    companion object {
        private const val BUILD_TYPE = "build_type"
        private const val ATTR_FLAVOR = "flavor"
    }
}
