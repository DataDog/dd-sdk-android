/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.tracer.DatadogTracer

/**
 * PoC Phases 1-2 — Compose initial-display span waterfall for Datadog.
 *
 * Each tagged composable's initial composition and first draw are *measured* during the first
 * frame and *buffered*; once the screen's first full draw completes the whole APM span tree is
 * built at once and a `firstRendered` RUM resource is emitted carrying the root span's
 * trace/span IDs. Opening that resource in the RUM Explorer reveals the waterfall:
 *
 * ```
 * ui.load                    firstRendered
 * |- ui.compose.composition  Jetpack Compose Initial Composition
 * |  |- ui.compose           <tag>
 * |- ui.compose.rendering    Jetpack Compose Initial Render
 *    |- ui.render            <tag>
 * ```
 *
 * Spans are not opened inline during composition/draw: children must be parented under a root
 * whose bounds are only known at the end, and buffering keeps the draw path allocation-light.
 *
 * Not production code — a sample-app PoC.
 */

@Immutable
internal class ImmutableHolder<T>(var item: T)

/** Composition-local carrying the active controller, or null when not inside a [DatadogFirstRenderRoot]. */
internal val LocalDatadogFirstRender = compositionLocalOf<FirstRenderController?> { null }

/** A single measured composition or render interval, in [System.nanoTime] units. */
private data class RecordedInterval(
    val tag: String,
    val startNanos: Long,
    val endNanos: Long
)

/**
 * Owns the span lifecycle for a single screen's initial display.
 * Created once (remembered) by [DatadogFirstRenderRoot].
 *
 * Collects composition/render intervals while the first frame is being produced, then emits the
 * span tree + linked RUM resource from [onFirstRenderComplete]. Once completed, nothing further
 * is recorded or emitted.
 */
internal class FirstRenderController(
    private val rum: RumMonitor,
    private val tracer: DatadogTracer,
    private val screenName: String
) {
    // Anchor pair mapping the monotonic clock onto wall-clock microseconds. Captured once, at
    // controller creation, i.e. as the screen starts composing.
    private val anchorNanos = System.nanoTime()
    private val anchorMicros = System.currentTimeMillis() * MICROS_PER_MILLI

    private val compositions = mutableListOf<RecordedInterval>()
    private val renders = mutableListOf<RecordedInterval>()
    private var completed = false

    fun recordComposition(tag: String, startNanos: Long, endNanos: Long) {
        if (!completed) compositions += RecordedInterval(tag, startNanos, endNanos)
    }

    fun recordRender(tag: String, startNanos: Long, endNanos: Long) {
        if (!completed) renders += RecordedInterval(tag, startNanos, endNanos)
    }

    /**
     * Called from the root's draw callback right after the first full draw. Builds the span tree
     * from everything buffered so far and links it to a `firstRendered` RUM resource.
     */
    fun onFirstRenderComplete(endNanos: Long) {
        if (completed) return
        completed = true

        val recorded = compositions + renders
        if (recorded.isEmpty()) {
            Log.w(TAG, "First render complete for screen '$screenName' with no tagged composables")
            return
        }

        val startNanos = recorded.minOf { it.startNanos }

        // Root: the span the "firstRendered" resource points at. Finished last, so it encloses
        // the whole tree.
        val root = tracer.buildSpan(OP_LOAD)
            .withResourceName(RESOURCE_NAME)
            .withStartTimestamp(toMicros(startNanos))
            .start()

        emitPhase(root, OP_COMPOSITION, COMPOSITION_RESOURCE, OP_COMPOSE, compositions)
        emitPhase(root, OP_RENDERING, RENDERING_RESOURCE, OP_RENDER, renders)

        root.finish(toMicros(endNanos))

        emitResource(root, startNanos)

        Log.i(
            TAG,
            "First render complete for screen '$screenName' in " +
                    "${(endNanos - startNanos) / NANOS_PER_MILLI}ms — " +
                    "${compositions.size} composition / ${renders.size} render spans"
        )
    }

    /** Builds one phase parent span plus a child span per buffered interval. */
    private fun emitPhase(
        root: DatadogSpan,
        phaseOperation: String,
        phaseResource: String,
        childOperation: String,
        intervals: List<RecordedInterval>
    ) {
        if (intervals.isEmpty()) return

        val parent = tracer.buildSpan(phaseOperation)
            .withResourceName(phaseResource)
            .withParentSpan(root)
            .withStartTimestamp(toMicros(intervals.minOf { it.startNanos }))
            .start()

        intervals.forEach { interval ->
            tracer.buildSpan(childOperation)
                .withResourceName(interval.tag)
                .withParentSpan(parent)
                .withStartTimestamp(toMicros(interval.startNanos))
                .start()
                .finish(toMicros(interval.endNanos))
        }

        parent.finish(toMicros(intervals.maxOf { it.endNanos }))
    }

    /**
     * Emits the RUM resource carrying the root span's IDs, the same way
     * [com.datadog.android.okhttp.DatadogInterceptor] links an OkHttp resource to its trace.
     */
    private fun emitResource(root: DatadogSpan, startNanos: Long) {
        val traceId = root.context().traceId.toHexString()
        val spanId = root.context().spanId.toString()

        rum.startResource(
            key = RESOURCE_NAME,
            method = RumResourceMethod.GET,
            url = RESOURCE_NAME,
            attributes = mapOf(
                RumAttributes.INTERNAL_TIMESTAMP to toMicros(startNanos) / MICROS_PER_MILLI
            )
        )
        rum.stopResource(
            key = RESOURCE_NAME,
            statusCode = 200,
            size = null,
            kind = RumResourceKind.NATIVE,
            attributes = mapOf(
                RumAttributes.TRACE_ID to traceId,
                RumAttributes.SPAN_ID to spanId,
                RumAttributes.RULE_PSR to 1.0
            )
        )

        Log.i(TAG, "Emitted '$RESOURCE_NAME' resource linked to trace_id=$traceId span_id=$spanId")
    }

    /** Maps a [System.nanoTime] reading onto the absolute microsecond timestamps spans expect. */
    private fun toMicros(nanoTime: Long): Long =
        anchorMicros + (nanoTime - anchorNanos) / NANOS_PER_MICRO

    internal companion object {
        private const val TAG = "DDFirstRender"
        private const val RESOURCE_NAME = "firstRendered"
        private const val MICROS_PER_MILLI = 1_000L
        private const val NANOS_PER_MICRO = 1_000L
        private const val NANOS_PER_MILLI = 1_000_000L

        const val OP_LOAD = "ui.load"
        const val OP_COMPOSITION = "ui.compose.composition"
        const val OP_RENDERING = "ui.compose.rendering"
        const val OP_COMPOSE = "ui.compose"
        const val OP_RENDER = "ui.render"
        const val COMPOSITION_RESOURCE = "Jetpack Compose Initial Composition"
        const val RENDERING_RESOURCE = "Jetpack Compose Initial Render"
    }
}

/**
 * Wraps a screen to measure its initial display as an APM span tree.
 * Provides the controller to descendant [DatadogTrace] calls, and emits the whole trace once the
 * first full draw completes.
 *
 * @param screenName label included in the completion log message.
 */
@Composable
internal fun DatadogFirstRenderRoot(
    screenName: String = "screen",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val controller = remember {
        FirstRenderController(GlobalRumMonitor.get(), GlobalDatadogTracer.get(), screenName)
    }
    val firstDrawn = remember { ImmutableHolder(false) }

    CompositionLocalProvider(LocalDatadogFirstRender provides controller) {
        Box(
            modifier = modifier.drawWithContent {
                drawContent()
                if (!firstDrawn.item) {
                    firstDrawn.item = true
                    controller.onFirstRenderComplete(System.nanoTime())
                }
            },
            propagateMinConstraints = true
        ) {
            content()
        }
    }
}

/**
 * Tags a composable so its initial composition and first draw become child spans of the screen's
 * initial-display trace. No-ops gracefully when used outside a [DatadogFirstRenderRoot].
 *
 * Produces:
 * - a `ui.compose` span named `<tag>`, covering the composition of this node's content
 * - a `ui.render` span named `<tag>`, covering this node's first draw pass
 */
@Composable
internal fun DatadogTrace(
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val controller = LocalDatadogFirstRender.current
    val firstRendered = remember { ImmutableHolder(false) }
    val compositionStartNs = System.nanoTime()

    Box(
        modifier = modifier.drawWithContent {
            val renderStartNs = if (!firstRendered.item) System.nanoTime() else 0L
            drawContent()
            if (!firstRendered.item) {
                firstRendered.item = true
                controller?.recordRender(tag, renderStartNs, System.nanoTime())
            }
        },
        propagateMinConstraints = true
    ) {
        content()
    }

    controller?.recordComposition(tag, compositionStartNs, System.nanoTime())
}
