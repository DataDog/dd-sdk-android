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
import com.datadog.android.rum.coroutines.sendErrorToDatadog
import com.datadog.android.sample.BuildConfig
import com.datadog.android.sample.data.Result
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.coroutines.CoroutineScopeSpan
import com.datadog.android.trace.coroutines.asyncTraced
import com.datadog.android.trace.coroutines.awaitTraced
import com.datadog.android.trace.coroutines.launchTraced
import com.datadog.android.trace.coroutines.withContextTraced
import com.datadog.android.vendor.sample.LocalServer
import com.launchdarkly.eventsource.EventHandler
import com.launchdarkly.eventsource.EventSource
import com.launchdarkly.eventsource.MessageEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.time.Duration
import java.util.Locale
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("StringLiteralDuplication", "TooManyFunctions")
internal class TracesViewModel(
    private val okHttpClient: OkHttpClient,
    private val localServer: LocalServer
) : ViewModel() {

    private var asyncOperationJob: Job? = null
    private var networkRequestJob: Job? = null
    private var eventSource: EventSource? = null
    private var sseEventHandler: SseEventHandler? = null

    @Suppress("CheckInternal")
    private val asyncOperationLogger: Logger by lazy {
        Logger.Builder()
            .setName("async_operation")
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

    @Suppress("MagicNumber")
    fun startAsyncOperation(
        onProgress: (Int) -> Unit = {},
        onDone: () -> Unit = {}
    ) {
        asyncOperationJob?.cancel()
        asyncOperationJob = viewModelScope.launchTraced("AsyncOperation", Dispatchers.Default) {
            logErrorMessage("Test error log in async operation")
            asyncOperationLogger.v("Starting Async Operation...")

            val count = (Random().nextInt() % 50) + 50
            logMessage("Async op loops $count times")
            var actualCount = 0

            for (progress in 0 until count) {
                ensureActive()
                withContext(Dispatchers.Main.immediate) {
                    onProgress(progress)
                }
                delay(((progress * progress).toDouble() / 100.0).toLong())
                actualCount++
            }

            logAttributes(mapOf("wanted_count" to count, "actual_count" to actualCount))
            asyncOperationLogger.v("Finishing Async Operation...")
            withContext(Dispatchers.Main.immediate) {
                onDone()
            }
        }
    }

    fun startCoroutineOperation(
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launchTraced("startCoroutineOperation", Dispatchers.Main) {
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
        launchRequest(
            url = localServer.getUrl(),
            onResponse = onResponse,
            onException = onException,
            onCancel = onCancel
        )
    }

    fun start404Request(
        onResponse: (Response) -> Unit,
        onException: (Throwable) -> Unit,
        onCancel: () -> Unit
    ) {
        launchRequest(
            url = "https://www.datadoghq.com/notfound",
            onResponse = onResponse,
            onException = onException,
            onCancel = onCancel
        )
    }

    fun startSseRequest(
        onResponse: () -> Unit,
        onException: (Throwable) -> Unit
    ) {
        networkRequestJob?.cancel()
        closeEventSource()

        val eventHandler = SseEventHandler(viewModelScope, onResponse, onException)
        val url = localServer.sseUrl()
        sseEventHandler = eventHandler
        networkRequestJob = viewModelScope.launch {
            try {
                val newEventSource = withContext(Dispatchers.IO) {
                    EventSource.Builder(eventHandler, URI.create(url))
                        .client(okHttpClient)
                        .connectTimeout(Duration.ofSeconds(3))
                        .backoffResetThreshold(Duration.ofSeconds(3))
                        .build()
                }
                eventSource = newEventSource
                newEventSource?.start()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Response", "Error", e)
                onException(e)
            }
        }
    }

    fun stopAsyncOperations() {
        asyncOperationJob?.cancel()
        networkRequestJob?.cancel()
        asyncOperationJob = null
        networkRequestJob = null
        closeEventSource()
        localServer.stop()
    }

    override fun onCleared() {
        closeEventSource()
        super.onCleared()
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
        scope.logMessage("The answer to life, the universe and everything is… $x")
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
                            logErrorMessage("got user $it")
                        }
                    }
            } catch (e: Throwable) {
                logThrowable(e)
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

    // region Network requests

    private fun launchRequest(
        url: String,
        onResponse: (Response) -> Unit,
        onException: (Throwable) -> Unit,
        onCancel: () -> Unit
    ) {
        networkRequestJob?.cancel()
        closeEventSource()
        val currentActiveMainSpan = GlobalDatadogTracer.get().activeSpan()
        networkRequestJob = viewModelScope.launch {
            val result = runInterruptible(Dispatchers.IO) {
                performRequest(url, currentActiveMainSpan)
            }
            handleResult(result, onResponse, onException, onCancel)
        }
    }

    @Suppress("TooGenericExceptionCaught", "LogNotTimber")
    private fun performRequest(url: String, parentSpan: DatadogSpan?): Result {
        val builder = Request.Builder()
            .get()
            .url(url)

        if (parentSpan != null) {
            builder.tag(DatadogSpan::class.java, parentSpan)
        }

        return try {
            val response = okHttpClient.newCall(builder.build()).execute()
            val body = response.body
            if (body != null) {
                val content = body.string()
                // Necessary to consume the response
                Log.d("Response", content)
            }
            Result.Success(response)
        } catch (e: Exception) {
            Log.e("Response", "Error", e)
            Result.Failure(throwable = e)
        }
    }

    private fun handleResult(
        result: Result,
        onResponse: (Response) -> Unit,
        onException: (Throwable) -> Unit,
        onCancel: () -> Unit
    ) {
        when (result) {
            is Result.Success<*> -> onResponse(result.data as Response)
            is Result.Failure -> result.throwable?.let(onException) ?: onCancel()
        }
    }

    // endregion

    // region Server-sent events

    private class SseEventHandler(
        private val coroutineScope: CoroutineScope,
        private val onResponse: () -> Unit,
        private val onException: (Throwable) -> Unit
    ) : EventHandler {
        private val active = AtomicBoolean(true)

        fun cancel() {
            active.set(false)
        }

        override fun onOpen() {
            Log.i("SSE", "onOpen")
        }

        override fun onError(e: Throwable?) {
            Log.e("SSE", "onError", e)
            if (e != null) {
                dispatch { onException(e) }
            }
        }

        override fun onComment(comment: String?) {
            Log.i("SSE", "onComment: $comment")
        }

        override fun onMessage(message: String?, event: MessageEvent?) {
            Log.i("SSE", "onMessage: $message | $event")
        }

        override fun onClosed() {
            dispatch(onResponse)
        }

        private fun dispatch(block: () -> Unit) {
            if (active.get()) {
                coroutineScope.launch {
                    if (active.get()) {
                        block()
                    }
                }
            }
        }
    }

    private fun closeEventSource() {
        sseEventHandler?.cancel()
        sseEventHandler = null
        eventSource?.close()
        eventSource = null
    }

    // endregion

    companion object {
        const val ATTR_FLAVOR = "flavor"
    }
}
