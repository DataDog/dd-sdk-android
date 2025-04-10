/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.traces

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import com.datadog.android.log.Logger
import com.datadog.android.ndk.tracer.NdkTracer
import com.datadog.android.okhttp.otel.addParentSpan
import com.datadog.android.sample.BuildConfig
import com.datadog.android.vendor.sample.LocalServer
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.context.Scope
import okhttp3.OkHttpClient
import okhttp3.Request

@Suppress("DEPRECATION")
internal class OtelTracesViewModel(
    private val okHttpClient: OkHttpClient,
    private val localServer: LocalServer,
    private val ndkTracer: NdkTracer

) : ViewModel() {

    private var asyncOperationTask: AsyncTask<Unit, Unit, Unit>? = null
    private var chainedContextsTask: AsyncTask<Unit, Unit, Unit>? = null
    private var linkedSpansTask: AsyncTask<Unit, Unit, Unit>? = null

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
        asyncOperationTask = AsyncOperationTask(onProgress, onDone)
        asyncOperationTask?.execute()
    }

    fun startChainedContexts(onDone: () -> Unit = {}) {
        chainedContextsTask = ChainedContextsTask(
            localServer.getUrl(),
            okHttpClient,
            onDone
        )
        chainedContextsTask?.execute()
    }

    fun startLinkedSpans(onDone: () -> Unit = {}) {
        linkedSpansTask = LinkedSpansTask(onDone)
        linkedSpansTask?.execute()
    }

    fun stopAsyncOperations() {
        asyncOperationTask?.cancel(true)
        chainedContextsTask?.cancel(true)
        linkedSpansTask?.cancel(true)
    }

    fun startRustTracerStressTest(onDone: () -> Unit = {}){
        spawnNdkTracerStressTest(onDone)
    }

    private fun spawnNdkTracerStressTest(onDone: () -> Unit){
        Thread {
            for (i in 0..1000) {
                val span = ndkTracer.startSpan("stressTestSpan_$i")
                Thread.sleep(100)
                span?.finish()
            }
            ndkTracer.dumpMetrics()
            Handler(Looper.getMainLooper()).post {
                onDone()
            }
        }.apply {
            start()
            join()
        }
    }

    // region AsyncOperationTask

    private class AsyncOperationTask(
        val onProgress: (Int) -> Unit,
        val onDone: () -> Unit
    ) : AsyncTask<Unit, Unit, Unit>() {
        val tracer: Tracer = GlobalOpenTelemetry
            .get()
            .getTracer(OtelTracesViewModel::class.java.simpleName)
        val parentSpan: Span = tracer
            .spanBuilder("Executing Async Operation")
            .startSpan()
        val scope: Scope = parentSpan.makeCurrent()

        private val logger: Logger by lazy {
            Logger.Builder()
                .setName("async_task")
                .setLogcatLogsEnabled(true)
                .build()
                .apply {
                    addTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
                    addTag(BUILD_TYPE, BuildConfig.BUILD_TYPE)
                }
        }

        @Deprecated("Deprecated in Java")
        override fun onPreExecute() {
            val span = tracer
                .spanBuilder("OnPreExecute")
                .startSpan()
            super.onPreExecute()
            span.end()
        }

        @Suppress("MagicNumber")
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Unit?) {
            val asyncOperationSpan = tracer
                .spanBuilder("AsyncOperation")
                .setParent(Context.current().with(parentSpan))
                .startSpan()
            logger.v("Starting Async Operation...")
            for (i in 0..100) {
                if (isCancelled) {
                    break
                }
                onProgress(i)
                Thread.sleep(((i * i).toDouble() / 100.0).toLong())
            }
            logger.v("Finishing Async Operation...")
            asyncOperationSpan.end()
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(result: Unit?) {
            val span = tracer
                .spanBuilder("OnPostExecute")
                .startSpan()
            if (!isCancelled) {
                onDone()
            }
            span.end()
            // close the current scope
            scope.close()
            // finish the parent span
            parentSpan.end()
        }
    }

    // endregion

    // region ChainedContextsTask

    private class ChainedContextsTask(
        private val url: String,
        private val okHttpClient: OkHttpClient,
        val onDone: () -> Unit
    ) : AsyncTask<Unit, Unit, Unit>() {
        private val tracer: Tracer = GlobalOpenTelemetry.get()
            .getTracer("chainedContexts")
        private val email = "john.doe@example.com"
        private val username = "John Doe"
        private val emailKey: ContextKey<String> = ContextKey.named("email")
        private val usernameKey: ContextKey<String> = ContextKey.named("username")
        private val context: Context =
            Context.current().with(emailKey, email).with(usernameKey, username)
        val startSpan: Span = tracer
            .spanBuilder("submitForm with chained contexts")
            .setParent(context)
            .startSpan()
        val scope: Scope = startSpan.makeCurrent()

        private val logger: Logger by lazy {
            Logger.Builder()
                .setName("chained-contexts-task")
                .setLogcatLogsEnabled(true)
                .build()
                .apply {
                    addTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
                    addTag(BUILD_TYPE, BuildConfig.BUILD_TYPE)
                }
        }

        @Suppress("MagicNumber")
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Unit?) {
            val processingFormSpan = tracer
                .spanBuilder("processingForm")
                .setParent(Context.current().with(startSpan))
                .startSpan()
            val email = context.get(emailKey)
            val username = context.get(usernameKey)
            val formScope = processingFormSpan.makeCurrent()
            val processingSanitization = tracer
                .spanBuilder("formSanitization")
                .startSpan()
            logger.v("Sanitizing email: $email")
            logger.v("Sanitizing username: $username")
            Thread.sleep(2000)
            processingSanitization.end()
            val request = Request.Builder()
                .get()
                .url(url)
                .addParentSpan(processingFormSpan)
                .build()
            okHttpClient.newCall(request).execute()
            formScope.close()
            processingFormSpan.end()
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(result: Unit?) {
            scope.close()
            startSpan.end()
            onDone()
        }
    }

    // endregion

    // region LinkedSpansTask

    private class LinkedSpansTask(
        val onDone: () -> Unit
    ) : AsyncTask<Unit, Unit, Unit>() {
        private val email = "john.doe@example.com"
        private val username = "John Doe"
        private val tracer: Tracer = GlobalOpenTelemetry.get()
            .getTracer("spanLinks")
        val startSpan: Span = tracer
            .spanBuilder("submitForm with linked spans")
            .startSpan()
        val scope: Scope = startSpan.makeCurrent()

        private val logger: Logger by lazy {
            Logger.Builder()
                .setName("chained-contexts-task")
                .setLogcatLogsEnabled(true)
                .build()
                .apply {
                    addTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
                    addTag(BUILD_TYPE, BuildConfig.BUILD_TYPE)
                }
        }

        @Suppress("MagicNumber")
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Unit?) {
            val processingFormSpan = tracer
                .spanBuilder("processingForm")
                .setParent(Context.current().with(startSpan))
                .startSpan()
            val formScope = processingFormSpan.makeCurrent()
            val attributes = Attributes
                .builder()
                .put("email", email)
                .put("username", username)
                .build()
            val processingSanitization = tracer
                .spanBuilder("formSanitization")
                .addLink(processingFormSpan.spanContext, attributes)
                .startSpan()
            logger.v("Sanitizing email")
            logger.v("Sanitizing username")
            Thread.sleep(2000)
            processingSanitization.end()
            Thread.sleep(5000)
            formScope.close()
            processingFormSpan.end()
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(result: Unit?) {
            scope.close()
            startSpan.end()
            onDone()
        }
    }

    // endregion

    companion object {
        private const val BUILD_TYPE = "build_type"
        private const val ATTR_FLAVOR = "flavor"
    }
}
