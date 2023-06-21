/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.traces

import android.os.AsyncTask
import android.util.Log
import androidx.lifecycle.ViewModel
import com.datadog.android.ktx.coroutine.CoroutineScopeSpan
import com.datadog.android.ktx.coroutine.asyncTraced
import com.datadog.android.ktx.coroutine.awaitTraced
import com.datadog.android.ktx.coroutine.launchTraced
import com.datadog.android.ktx.coroutine.sendErrorToDatadog
import com.datadog.android.ktx.coroutine.withContextTraced
import com.datadog.android.ktx.tracing.withinSpan
import com.datadog.android.log.Logger
import com.datadog.android.sample.BuildConfig
import com.datadog.android.sample.data.Result
import com.datadog.android.sample.server.LocalServer
import io.opentracing.Span
import io.opentracing.log.Fields
import io.opentracing.util.GlobalTracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.Locale
import java.util.Random

@Suppress("DEPRECATION")
internal class TracesViewModel(private val okHttpClient: OkHttpClient) : ViewModel() {

    private var asyncOperationTask: AsyncTask<Unit, Unit, Unit>? = null
    private var networkRequestTask: AsyncTask<Unit, Unit, Result>? = null
    private var localServer: LocalServer = LocalServer()

    private val scope = MainScope()

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

    fun startCoroutineOperation(
        onDone: () -> Unit = {}
    ) {
        scope.launchTraced("startCoroutineOperation", Dispatchers.Main) {
            setTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
            performTask(this)
            performFlowTask()

            onDone()
        }
    }

    fun startRequest(
        onResponse: (Response) -> Unit,
        onException: (Throwable) -> Unit,
        onCancel: () -> Unit
    ) {
        networkRequestTask = RequestTask(
            localServer.getUrl(),
            okHttpClient,
            onResponse,
            onException,
            onCancel
        )
        networkRequestTask?.execute()
    }

    fun start404Request(
        onResponse: (Response) -> Unit,
        onException: (Throwable) -> Unit,
        onCancel: () -> Unit
    ) {
        networkRequestTask = RequestTask(
            "https://www.datadoghq.com/notfound",
            okHttpClient,
            onResponse,
            onException,
            onCancel
        )
        networkRequestTask?.execute()
    }

    fun stopAsyncOperations() {
        asyncOperationTask?.cancel(true)
        networkRequestTask?.cancel(true)
        asyncOperationTask = null
        networkRequestTask = null
        localServer.stop()
    }

    // region Flow/Coroutine

    @Suppress("MagicNumber")
    private suspend fun performTask(scope: CoroutineScopeSpan) {
        delay(100)

        val deferred = scope.asyncTraced("coroutine async", Dispatchers.IO) {
            setTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
            delay(2000)
            42
        }
        delay(100)

        withContextTraced("coroutine unconfined task", Dispatchers.Unconfined) {
            setTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
            delay(500)
        }

        delay(100)

        withContextTraced("coroutine task", Dispatchers.Default) {
            setTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
            delay(500)
        }

        delay(100)

        val x = deferred.awaitTraced("coroutine await")
        scope.log(mapOf(Fields.MESSAGE to "The answer to life, the universe and everything is… $x"))
    }

    @Suppress("TooGenericExceptionCaught", "MagicNumber")
    private suspend fun performFlowTask() {
        delay(100)
        withContextTraced("coroutine flow collect", Dispatchers.Default) {
            try {
                setTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
                val flow = getFlow()
                flow.sendErrorToDatadog()
                flow.map {
                    it.replaceFirstChar { c ->
                        if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString()
                    }
                }
                    .filter { it.length > 4 }
                    .collect {
                        if (Random().nextInt(5) == 0) {
                            error("Your flow just dried out…")
                        } else {
                            log(mapOf(Fields.MESSAGE to "got user $it"))
                        }
                    }
            } catch (e: Throwable) {
                log(mapOf(Fields.ERROR_OBJECT to e))
            }
        }
    }

    @Suppress("MagicNumber")
    private fun getFlow(): Flow<String> {
        return flow {
            val names = listOf("jake", "cassie", "marco", "rachel", "tobias", "ax", "david")
            for (name in names) {
                delay(500)
                emit(name)
            }
        }
    }

    // endregion

    // region RequestTask

    private class RequestTask(
        private val url: String,
        private val okHttpClient: OkHttpClient,
        private val onResponse: (Response) -> Unit,
        private val onException: (Throwable) -> Unit,
        private val onCancel: () -> Unit
    ) : AsyncTask<Unit, Unit, Result>() {
        private var currentActiveMainSpan: Span? = null

        @Deprecated("Deprecated in Java")
        override fun onPreExecute() {
            super.onPreExecute()
            currentActiveMainSpan = GlobalTracer.get().activeSpan()
        }

        @Deprecated("Deprecated in Java")
        @Suppress("TooGenericExceptionCaught", "LogNotTimber")
        override fun doInBackground(vararg params: Unit?): Result {
            val builder = Request.Builder()
                .get()
                .url(url)

            if (currentActiveMainSpan != null) {
                builder.tag(
                    Span::class.java,
                    currentActiveMainSpan
                )
            }
            val request = builder.build()
            return try {
                val response = okHttpClient.newCall(request).execute()
                val body = response.body()
                if (body != null) {
                    val content: String = body.string()
                    // Necessary to consume the response
                    Log.d("Response", content)
                }
                Result.Success(response)
            } catch (e: Exception) {
                Log.e("Response", "Error", e)
                Result.Failure(throwable = e)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(result: Result) {
            super.onPostExecute(result)
            if (!isCancelled) {
                handleResult(result)
            }
        }

        private fun handleResult(
            result: Result
        ) {
            when (result) {
                is Result.Success<*> -> {
                    onResponse(result.data as Response)
                }
                is Result.Failure -> {
                    if (result.throwable != null) {
                        onException(result.throwable)
                    } else {
                        onCancel()
                    }
                }
            }
        }
    }

    // endregion

    // region AsyncOperationTask

    private class AsyncOperationTask(
        val onProgress: (Int) -> Unit,
        val onDone: () -> Unit
    ) : AsyncTask<Unit, Unit, Unit>() {

        var activeSpanInMainThread: Span? = null

        @Suppress("CheckInternal")
        private val logger: Logger by lazy {
            Logger.Builder()
                .setLoggerName("async_task")
                .setLogcatLogsEnabled(true)
                .build()
                .apply {
                    addTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
                    addTag("build_type", BuildConfig.BUILD_TYPE)
                }
        }

        @Deprecated("Deprecated in Java")
        override fun onPreExecute() {
            super.onPreExecute()
            activeSpanInMainThread = GlobalTracer.get().activeSpan()
        }

        @Suppress("MagicNumber")
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Unit?) {
            withinSpan("AsyncOperation", activeSpanInMainThread) {
                logger.v("Starting Async Operation...")

                for (i in 0..100) {
                    if (isCancelled) {
                        break
                    }
                    onProgress(i)
                    Thread.sleep(((i * i).toDouble() / 100.0).toLong())
                }

                logger.v("Finishing Async Operation...")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(result: Unit?) {
            if (!isCancelled) {
                onDone()
            }
        }
    }

    // endregion

    companion object {
        const val ATTR_FLAVOR = "flavor"
    }
}
