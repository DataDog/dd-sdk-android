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
import io.opentracing.Span
import io.opentracing.util.GlobalTracer
import java.lang.Exception

class TracesViewModel : ViewModel() {

    var asyncTask: AsyncTask<Unit, Unit, Unit>? = null
    fun startAsyncOperation(onDone: () -> Unit = {}) {
        asyncTask = Task(onDone)
        asyncTask?.execute()
    }

    fun stopAsyncOperations() {
        asyncTask?.cancel(true)
        asyncTask = null
    }

    private class Task(val onDone: () -> Unit) : AsyncTask<Unit, Unit, Unit>() {
        var activeSpanInMainThread: Span? = null

        private val logger: Logger by lazy {
            Logger.Builder()
                .setServiceName("android-sample-kotlin")
                .setLoggerName("async_task")
                .setLogcatLogsEnabled(true)
                .build()
                .apply {
                    addTag("flavor", BuildConfig.FLAVOR)
                    addTag("build_type", BuildConfig.BUILD_TYPE)
                }
        }

        override fun onPreExecute() {
            super.onPreExecute()
            activeSpanInMainThread = GlobalTracer.get().activeSpan()
        }

        override fun doInBackground(vararg params: Unit?) {
            val spanBuilder = GlobalTracer.get()
                .buildSpan("AsyncOperation")
            activeSpanInMainThread?.let {
                spanBuilder.asChildOf(it)
            }
            val span = spanBuilder.start()
            if (isCancelled) {
                return
            }
            try {
                val scope = GlobalTracer.get().activateSpan(span)
                logger.v("Starting Async Operation...")
                // just emulate an async operation here
                Thread.sleep(10000)
                logger.v("Finishing Async Operation...")
                scope.close()
            } catch (e: Exception) {
                span.log(e.message)
            } finally {
                span.finish()
            }
        }

        override fun onPostExecute(result: Unit?) {
            onDone()
        }
    }
}
