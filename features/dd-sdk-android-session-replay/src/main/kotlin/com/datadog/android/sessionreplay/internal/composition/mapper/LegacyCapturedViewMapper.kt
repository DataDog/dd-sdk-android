/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.composition.EffectivePrivacy
import com.datadog.android.sessionreplay.internal.composition.isOptionSelectorSafely
import com.datadog.android.sessionreplay.internal.composition.resolveEffectivePrivacy
import com.datadog.android.sessionreplay.internal.putTouchOverrideArea
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.internal.recorder.mapper.HiddenViewMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.InteropViewCallback
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.recorder.mapper.TraverseAllChildrenMapper
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import java.util.IdentityHashMap

/** Retains the existing extension API and widget privacy rules; only copied mapper output reaches construction. */
internal class LegacyCapturedViewMapper<T : View>(
    private val mapper: WireframeMapper<T>,
    private val allMappers: List<MapperTypeWrapper<*>>,
    private val fallbackMapper: WireframeMapper<View>,
    private val imageHelper: ImageWireframeHelper,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val optionSelectorDetector: OptionSelectorDetector,
    private val internalLogger: InternalLogger,
    val collectTouchPrivacy: Boolean = true
) : CapturedViewMapper<T> {
    @UiThread
    override fun map(view: T, mappingContext: CapturedMappingContext): CapturedViewMapperResult =
        capture(view, mappingContext).map(mappingContext)

    @UiThread
    @Suppress("TooGenericExceptionCaught") // A third-party mapper must not escape into application drawing.
    override fun capture(view: T, mappingContext: CapturedMappingContext): CapturedViewData =
        captureWithHelper(view, mappingContext, imageHelper)

    /** Reads custom touch rules in a skipped visual subtree without delivering its image resources. */
    @UiThread
    fun captureTouchPrivacy(view: T, mappingContext: CapturedMappingContext) {
        captureWithHelper(view, mappingContext.copy(pendingMappingSink = {}), TouchPrivacyOnlyImageWireframeHelper)
    }

    @UiThread
    @Suppress("TooGenericExceptionCaught") // Optional mapper failures cannot escape into application drawing.
    private fun captureWithHelper(
        view: T,
        mappingContext: CapturedMappingContext,
        helper: ImageWireframeHelper
    ): CapturedViewData {
        val pending = PendingViewMapping(mapper !is TraverseAllChildrenMapper)
        mappingContext.pendingMappingSink(pending)
        val collector = if (collectTouchPrivacy) MapperTouchPrivacyCollector() else null
        var succeeded = false
        try {
            val manager = if (collector == null) {
                touchPrivacyManager
            } else {
                touchPrivacyManager.withOverrideAreaSink(collector::add)
            }
            pending.mapped(mapWithContext(view, mappingContext, pending, manager, helper))
            succeeded = true
        } catch (e: Exception) {
            pending.failed()
            logFailure(e)
        } catch (e: LinkageError) {
            pending.failed()
            logFailure(e)
        } finally {
            if (collector != null) {
                val areas = collector.close()
                mappingContext.customTouchPrivacySink(if (succeeded) areas else null)
            }
        }
        return pending
    }

    @UiThread
    private fun mapWithContext(
        view: T,
        mappingContext: CapturedMappingContext,
        pending: PendingViewMapping,
        manager: TouchPrivacyManager,
        helper: ImageWireframeHelper
    ): List<MobileSegment.Wireframe> {
        val metrics = view.resources.displayMetrics
        val density = if (mappingContext.screenDensity > 0) mappingContext.screenDensity else 1f
        val interop = LegacyInterop(pending, mappingContext.shouldContinue)
        interop.visited[view] = true
        val context = MappingContext(
            SystemInformation(
                GlobalBounds(
                    0,
                    0,
                    (metrics.widthPixels / density).toLong(),
                    (metrics.heightPixels / density).toLong()
                ),
                view.resources.configuration.orientation,
                density
            ),
            helper,
            mappingContext.textAndInputPrivacy,
            mappingContext.imagePrivacy,
            manager,
            mappingContext.hasOptionSelectorParent,
            interop
        )
        return mapper.map(view, context, pending, internalLogger)
    }

    private fun logFailure(error: Throwable) = internalLogger.log(
        InternalLogger.Level.ERROR,
        InternalLogger.Target.TELEMETRY,
        { "Unable to capture an extension or widget mapper" },
        error,
        onlyOnce = true
    )

    private inner class LegacyInterop(
        private val pending: PendingViewMapping,
        private val shouldContinue: () -> Boolean
    ) : InteropViewCallback {
        val visited = IdentityHashMap<View, Boolean>()
        private val viewUtils = ViewUtilsInternal()

        @UiThread
        @Suppress("UnsafeThirdPartyFunctionCall") // Owned deque, checked access, validated view types.
        override fun map(view: View, mappingContext: MappingContext): List<MobileSegment.Wireframe> {
            val result = mutableListOf<MobileSegment.Wireframe>()
            val stack = ArrayDeque<Pair<View, MappingContext>>()
            stack.addLast(view to mappingContext)
            while (stack.isNotEmpty() && shouldContinue()) {
                val (current, inherited) = stack.removeLast()
                if (visited.put(current, true) != null || isFiltered(current)
                ) {
                    continue
                }
                val privacy = resolveEffectivePrivacy(
                    current,
                    EffectivePrivacy(
                        inherited.imagePrivacy,
                        inherited.textAndInputPrivacy
                    ),
                    internalLogger
                )
                val context = inherited.copy(
                    imagePrivacy = privacy.imagePrivacy,
                    textAndInputPrivacy = privacy.textAndInputPrivacy
                )
                val hidden = current.getTag(R.id.datadog_hidden) == true
                val resolved = if (hidden) {
                    HiddenViewMapper(DefaultViewIdentifierResolver, DefaultViewBoundsResolver)
                } else {
                    allMappers.firstOrNull { it.supportsView(current) }?.getUnsafeMapper()
                }
                val selected = resolved ?: fallbackMapper
                result += selected.map(current, context, pending, internalLogger)
                val traverse = resolved == null || selected is TraverseAllChildrenMapper
                if (!hidden && current is ViewGroup && traverse) {
                    val childContext = context.copy(
                        hasOptionSelectorParent = context.hasOptionSelectorParent ||
                            optionSelectorDetector.isOptionSelectorSafely(current, internalLogger)
                    )
                    for (index in current.childCount - 1 downTo 0) {
                        stack.addLast(
                            current.getChildAt(index) to childContext
                        )
                    }
                }
            }
            return result
        }

        @UiThread
        private fun isFiltered(view: View): Boolean = viewUtils.isNotVisible(view) ||
            viewUtils.isSystemNoise(view) || viewUtils.isOnSecondaryDisplay(view)
    }
}

/** Owns copied rectangles until map() returns; retained extension callbacks cannot modify a later pass. */
private class MapperTouchPrivacyCollector {
    private val lock = Any()

    @Suppress("DoubleMutabilityForCollection") // Null seals the collector and releases its map from retained callbacks.
    private var areas: MutableMap<Rect, TouchPrivacy>? = mutableMapOf()

    @Suppress("UnsafeThirdPartyFunctionCall") // Copy a valid Rect and mutate only this collector's map.
    fun add(bounds: Rect, privacy: TouchPrivacy) {
        synchronized(lock) {
            areas?.putTouchOverrideArea(Rect(bounds), privacy)
        }
    }

    fun close(): Map<Rect, TouchPrivacy> = synchronized(lock) {
        val captured = areas.orEmpty()
        areas = null
        captured
    }
}
