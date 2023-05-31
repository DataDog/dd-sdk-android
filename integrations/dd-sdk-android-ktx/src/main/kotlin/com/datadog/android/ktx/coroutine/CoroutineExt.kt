/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.coroutine

import com.datadog.android.ktx.tracing.withinSpan
import io.opentracing.Span
import io.opentracing.util.GlobalTracer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private const val TAG_DISPATCHER: String = "coroutine.dispatcher"

/**
 * Launches a new coroutine without blocking the current thread and returns a reference to the
 * coroutine as a [Job]. A span will be created around the coroutine code and sent to Datadog.
 *
 * See [launch] to learn more about launching a new coroutine.
 *
 * @param operationName the name of the [Span] created around the coroutine code.
 * @param context additional to [CoroutineScope.coroutineContext] context of the coroutine.
 * @param start coroutine start option. The default value is [CoroutineStart.DEFAULT].
 * @param block the coroutine code which will be invoked in the context of the provided scope.
 **/
fun CoroutineScope.launchTraced(
    operationName: String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScopeSpan.() -> Unit
): Job {
    val parentSpan = GlobalTracer.get().activeSpan()
    return launch(context, start) {
        withinCoroutineSpan(operationName, parentSpan, context, block)
    }
}

/**
 * Runs a new coroutine and **blocks** the current thread _interruptibly_ until its completion.
 * A span will be created around the coroutine code and sent to Datadog.
 *
 * This function should not be used from a coroutine. It is designed to bridge regular blocking code
 * to libraries that are written in suspending style, to be used in `main` functions and in tests.
 *
 * See [runBlocking] to learn more about running a coroutine waiting for completion.
 *
 * @param T the type returned by the traced Coroutine block
 * @param operationName the name of the [Span] created around the coroutine code.
 * @param context the context of the coroutine. The default value is an event loop on the current
 * thread.
 * @param block the coroutine code.
 */
fun <T> runBlockingTraced(
    operationName: String,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T {
    val parentSpan = GlobalTracer.get().activeSpan()
    return runBlocking(context) {
        withinCoroutineSpan(operationName, parentSpan, context, block)
    }
}

/**
 * Creates a coroutine and returns its future result as an implementation of [Deferred].
 * A span will be created around the coroutine code and sent to Datadog.
 *
 * See [async] to learn more about using deferred coroutine results.
 *
 * @param T the type returned by the traced Coroutine block
 * @param operationName the name of the [Span] created around the coroutine code.
 * @param context the context to use for the async block
 * @param start defines how the block is scheduled (use [CoroutineStart.LAZY] to start the
 * coroutine lazily)
 * @param block the coroutine code.
 */
@Suppress("DeferredIsResult")
fun <T : Any?> CoroutineScope.asyncTraced(
    operationName: String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScopeSpan.() -> T
): Deferred<T> {
    val parentSpan = GlobalTracer.get().activeSpan()
    return async(context, start) {
        withinCoroutineSpan(operationName, parentSpan, context, block)
    }
}

/**
 * Awaits for completion of this value without blocking a thread and resumes when deferred
 * computation is complete, returning the resulting value or throwing the corresponding exception if
 * the deferred was cancelled.
 * A span will be created around the completion and sent to Datadog.
 *
 * See [Deferred.await] to learn more about awaiting completion on a Deferred result.
 *
 * @param T the type returned by this [Deferred] instance
 * @param operationName the name of the [Span] created around the coroutine code.
 */
suspend fun <T : Any?> Deferred<T>.awaitTraced(operationName: String): T {
    val parentSpan = GlobalTracer.get().activeSpan()
    return withinSpan(operationName, parentSpan, false) {
        @Suppress("UnsafeThirdPartyFunctionCall") // handled by caller
        this@awaitTraced.await()
    }
}

/**
 * Calls the specified suspending block with a given coroutine context, suspends until it completes,
 * and returns the result.
 * A span will be created around the coroutine code and sent to Datadog.
 *
 * See [withContext] to learn more about running a coroutine within a specific [CoroutineContext].
 *
 * @param T the type returned by the traced operation
 * @param operationName the name of the [Span] created around the coroutine code.
 * @param context the context of the coroutine.
 * @param block the coroutine code.
 */
suspend fun <T : Any?> withContextTraced(
    operationName: String,
    context: CoroutineContext,
    block: suspend CoroutineScopeSpan.() -> T
): T {
    val parentSpan = GlobalTracer.get().activeSpan()
    return withContext(context) {
        withinCoroutineSpan(operationName, parentSpan, context, block)
    }
}

private suspend fun <T : Any?> CoroutineScope.withinCoroutineSpan(
    operationName: String,
    parentSpan: Span? = null,
    context: CoroutineContext,
    block: suspend CoroutineScopeSpan.() -> T
): T {
    return withinSpan(operationName, parentSpan, context != Dispatchers.Unconfined) {
        if (context is CoroutineDispatcher) {
            setTag(TAG_DISPATCHER, context.toString())
        }
        @Suppress("UnsafeThirdPartyFunctionCall") // handled by caller
        block(CoroutineScopeSpanImpl(this@withinCoroutineSpan, this))
    }
}
