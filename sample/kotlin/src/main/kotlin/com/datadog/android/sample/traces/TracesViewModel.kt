/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.traces

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datadog.android.log.Logger
import com.datadog.android.sample.BuildConfig
import com.datadog.android.sample.sampleTaskDispatcher
import com.datadog.android.sample.sampleTaskScope
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.coroutines.CoroutineScopeSpan
import com.datadog.android.trace.coroutines.asyncTraced
import com.datadog.android.trace.coroutines.awaitTraced
import com.datadog.android.trace.coroutines.launchTraced
import com.datadog.android.trace.coroutines.withContextTraced
import com.datadog.android.trace.withinSpan
import com.datadog.android.vendor.sample.LocalServer
import com.launchdarkly.eventsource.EventHandler
import com.launchdarkly.eventsource.EventSource
import com.launchdarkly.eventsource.MessageEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.time.Duration
import java.util.Locale
import java.util.Random
import kotlin.time.Duration.Companion.milliseconds

@Suppress("StringLiteralDuplication", "TooManyFunctions")
internal class TracesViewModel(
    private val okHttpClient: OkHttpClient,
    private val localServer: LocalServer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val unconfinedDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
) : ViewModel() {

    private val taskScope = sampleTaskScope()
    private val asyncLogger: Logger by lazy {
        Logger.Builder()
            .setName("async_task")
            .setLogcatLogsEnabled(true)
            .build()
            .apply {
                addTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
                addTag("build_type", BuildConfig.BUILD_TYPE)
            }
    }

    fun onResume() {
        localServer.start("https://www.datadoghq.com/")
    }

    fun onPause() {
        localServer.stop()
    }

    fun startAsyncOperation(onProgress: (Int) -> Unit = {}, onDone: () -> Unit = {}) {
        val parentSpan = GlobalDatadogTracer.get().activeSpan()
        taskScope.launch {
            runInterruptible(sampleTaskDispatcher) {
                performAsyncOperation(parentSpan) { progress ->
                    launch { onProgress(progress) }
                }
            }
            onDone()
        }
    }

    fun startCoroutineOperation(onDone: () -> Unit = {}) {
        viewModelScope.launchTraced("startCoroutineOperation") {
            setTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
            performTask(this)
            performFlowTask()
            onDone()
        }
    }

    fun startRequest(onResponse: (Response) -> Unit, onException: (Throwable) -> Unit) {
        startRequest(localServer.getUrl(), onResponse, onException)
    }

    fun start404Request(onResponse: (Response) -> Unit, onException: (Throwable) -> Unit) {
        startRequest("https://www.datadoghq.com/notfound", onResponse, onException)
    }

    fun startSseRequest(onResponse: () -> Unit, onException: (Throwable) -> Unit) {
        val url = localServer.sseUrl()
        taskScope.launch {
            val listener = SseEventHandler(
                onResponse = { launch { onResponse() } },
                onException = { error -> launch { onException(error) } }
            )
            val eventSourceClient = okHttpClient.newBuilder()
                .dispatcher(Dispatcher())
                .connectionPool(ConnectionPool())
                .build()
            EventSource.Builder(listener, URI.create(url))
                .client(eventSourceClient)
                .connectTimeout(SSE_TIMEOUT)
                .backoffResetThreshold(SSE_TIMEOUT)
                .build()
                .use { eventSource ->
                    runInterruptible(sampleTaskDispatcher) { eventSource.start() }
                    awaitCancellation()
                }
        }
    }

    fun stopAsyncOperations() {
        taskScope.coroutineContext.cancelChildren()
        localServer.stop()
    }

    @Suppress("LogNotTimber", "TooGenericExceptionCaught")
    private fun startRequest(url: String, onResponse: (Response) -> Unit, onException: (Throwable) -> Unit) {
        val parentSpan = GlobalDatadogTracer.get().activeSpan()
        val request = Request.Builder()
            .get()
            .url(url)
            .tag(DatadogSpan::class.java, parentSpan)
            .build()

        taskScope.launch {
            val response = try {
                runInterruptible(sampleTaskDispatcher) {
                    okHttpClient.newCall(request).execute().also {
                        it.body?.let { body -> Log.d("Response", body.string()) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Response", "Error", e)
                onException(e)
                return@launch
            }
            onResponse(response)
        }
    }

    private fun performAsyncOperation(parentSpan: DatadogSpan?, onProgress: (Int) -> Unit) {
        withinSpan("AsyncOperation", parentSpan) {
            logErrorMessage("Test error log in async operation")
            asyncLogger.v("Starting Async Operation...")

            val count = (Random().nextInt() % BASE_PROGRESS_COUNT) + BASE_PROGRESS_COUNT
            logMessage("Async op loops $count times")
            var actualCount = 0

            try {
                for (progress in 0 until count) {
                    onProgress(progress)
                    Thread.sleep(traceProgressDelayMillis(progress))
                    actualCount++
                }
            } catch (_: InterruptedException) {
                logMessage("Async operation cancelled")
            } finally {
                logAttributes(mapOf("wanted_count" to count, "actual_count" to actualCount))
                asyncLogger.v("Finishing Async Operation...")
            }
        }
    }

    // region Flow/Coroutine

    @Suppress("MagicNumber")
    private suspend fun performTask(scope: CoroutineScopeSpan) {
        delay(100.milliseconds)

        val deferred = scope.asyncTraced("coroutine async", ioDispatcher) {
            setTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
            delay(2000.milliseconds)
            42
        }
        delay(100.milliseconds)

        withContextTraced("coroutine unconfined task", unconfinedDispatcher) {
            setTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
            delay(500.milliseconds)
        }

        delay(100.milliseconds)

        withContextTraced("coroutine task", defaultDispatcher) {
            setTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
            delay(500.milliseconds)
        }

        delay(100.milliseconds)

        val x = deferred.awaitTraced("coroutine await")
        scope.logMessage("The answer to life, the universe and everything is… $x")
    }

    @Suppress("TooGenericExceptionCaught", "MagicNumber")
    private suspend fun performFlowTask() {
        delay(100.milliseconds)
        withContextTraced("coroutine flow collect", defaultDispatcher) {
            try {
                setTag(ATTR_FLAVOR, BuildConfig.FLAVOR)
                getFlow().map {
                    it.replaceFirstChar { c ->
                        if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString()
                    }
                }
                    .filter { it.length > 4 }
                    .collect {
                        if (Random().nextInt(5) == 0) {
                            error("Your flow just dried out…")
                        } else {
                            logErrorMessage("got user $it")
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logThrowable(e)
            }
        }
    }

    @Suppress("MagicNumber")
    private fun getFlow(): Flow<String> {
        return flow {
            val names = listOf("jake", "cassie", "marco", "rachel", "tobias", "ax", "david")
            for (name in names) {
                delay(500.milliseconds)
                emit(name)
            }
        }
    }

    // endregion

    @Suppress("LogNotTimber")
    private class SseEventHandler(
        private val onResponse: () -> Unit,
        private val onException: (Throwable) -> Unit
    ) : EventHandler {

        override fun onOpen() {
            Log.i("SSE", "onOpen")
        }

        override fun onError(e: Throwable?) {
            Log.e("SSE", "onError", e)
            e?.let { onException(it) }
        }

        override fun onComment(comment: String?) {
            Log.i("SSE", "onComment: $comment")
        }

        override fun onMessage(message: String?, event: MessageEvent?) {
            Log.i("SSE", "onMessage: $message | $event")
        }

        override fun onClosed() {
            onResponse()
        }
    }

    companion object {
        const val ATTR_FLAVOR = "flavor"
        private const val BASE_PROGRESS_COUNT = 50
        private val SSE_TIMEOUT = Duration.ofSeconds(3)
    }
}

private const val PROGRESS_DELAY_DIVISOR = 100

internal fun traceProgressDelayMillis(progress: Int): Long = (progress * progress / PROGRESS_DELAY_DIVISOR).toLong()
