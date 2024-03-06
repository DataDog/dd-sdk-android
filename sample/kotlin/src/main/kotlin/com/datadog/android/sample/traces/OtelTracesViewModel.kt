/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.traces

import android.os.AsyncTask
import androidx.lifecycle.ViewModel
import com.datadog.android.log.Logger
import com.datadog.android.sample.BuildConfig
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope

@Suppress("DEPRECATION")
internal class OtelTracesViewModel : ViewModel() {

    private var asyncOperationTask: AsyncTask<Unit, Unit, Unit>? = null

    fun startAsyncOperation(
        onProgress: (Int) -> Unit = {},
        onDone: () -> Unit = {}
    ) {
        asyncOperationTask = AsyncOperationTask(onProgress, onDone)
        asyncOperationTask?.execute()
    }

    fun stopAsyncOperations() {
        asyncOperationTask?.cancel(true)
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

        @Suppress("CheckInternal")
        private val logger: Logger by lazy {
            Logger.Builder()
                .setName("async_task")
                .setLogcatLogsEnabled(true)
                .build()
                .apply {
                    addTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
                    addTag("build_type", BuildConfig.BUILD_TYPE)
                }
        }

        @Deprecated("Deprecated in Java")
        override fun onPreExecute() {
            val span = tracer
                .spanBuilder("OnPreExecute")
                .setParent(Context.current())
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
            val parentContext = Context.current()
            val span = tracer
                .spanBuilder("OnPostExecute")
                .setParent(parentContext)
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

    companion object {
        const val ATTR_FLAVOR = "flavor"
    }
}
