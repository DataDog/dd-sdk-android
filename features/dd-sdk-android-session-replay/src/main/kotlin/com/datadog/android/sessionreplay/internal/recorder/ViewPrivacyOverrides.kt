/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Rect
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.recorder.MappingContext
import java.util.Locale

/**
 * Resolves [view]'s own per-view privacy tag overrides — set via
 * [com.datadog.android.sessionreplay.setSessionReplayImagePrivacy]/
 * [com.datadog.android.sessionreplay.setSessionReplayTextAndInputPrivacy] — against
 * [mappingContext]'s current (inherited) privacy levels, falling back to those when [view]
 * carries no override of its own.
 *
 * Shared by both traversals — [SnapshotProducer] (the default pipeline) and
 * [CompositionTreeBuilder] (the one actually used whenever `pixelCaptureEnabled` is set) — so a
 * tagged view behaves identically regardless of which is active. Originally lived only inside
 * [SnapshotProducer]: [CompositionTreeBuilder] built one [MappingContext] from the app-wide
 * config at the top of its traversal and never re-resolved it per view, silently ignoring every
 * per-view override for as long as pixel capture was enabled — extracting this here, rather than
 * leaving two separately-maintained copies, is specifically to close that gap without risking the
 * same divergence recurring later.
 */
internal fun resolveViewPrivacyOverrides(
    view: View,
    mappingContext: MappingContext,
    internalLogger: InternalLogger
): MappingContext {
    val imagePrivacy = try {
        val privacy = view.getTag(R.id.datadog_image_privacy) as? String
        if (privacy == null) {
            mappingContext.imagePrivacy
        } else {
            ImagePrivacy.valueOf(privacy)
        }
    } catch (e: IllegalArgumentException) {
        logInvalidPrivacyLevelError(internalLogger, e)
        mappingContext.imagePrivacy
    }

    val textAndInputPrivacy = try {
        val privacy = view.getTag(R.id.datadog_text_and_input_privacy) as? String
        if (privacy == null) {
            mappingContext.textAndInputPrivacy
        } else {
            TextAndInputPrivacy.valueOf(privacy)
        }
    } catch (e: IllegalArgumentException) {
        logInvalidPrivacyLevelError(internalLogger, e)
        mappingContext.textAndInputPrivacy
    }

    return mappingContext.copy(
        imagePrivacy = imagePrivacy,
        textAndInputPrivacy = textAndInputPrivacy
    )
}

/**
 * True if [view] was hidden via [com.datadog.android.sessionreplay.setSessionReplayHidden] — a
 * hidden view is replaced with a single "Hidden"-labeled placeholder and its children are never
 * traversed at all (see [com.datadog.android.sessionreplay.internal.recorder.mapper.HiddenViewMapper]).
 * Same sharing rationale as [resolveViewPrivacyOverrides]: [TreeViewTraversal] already checks this
 * for the default pipeline; [CompositionTreeBuilder] didn't check it at all before, silently
 * recording a supposedly-hidden view's real content whenever pixel capture was enabled.
 */
internal fun isViewHiddenForSessionReplay(view: View): Boolean = view.getTag(R.id.datadog_hidden) == true

/**
 * Registers [view]'s own [TouchPrivacy] tag override — set via
 * [com.datadog.android.sessionreplay.setSessionReplayTouchPrivacy] — as a screen-space area in
 * [mappingContext]'s [com.datadog.android.sessionreplay.internal.TouchPrivacyManager], if [view]
 * carries one. Unlike [resolveViewPrivacyOverrides], this doesn't change what gets mapped for
 * [view] itself — [TouchPrivacyManager] is a separate, shared accumulator (the same instance
 * flows through every node in a traversal) that only affects which on-screen taps are later shown
 * in the touch heatmap, so this should run for every view unconditionally, regardless of whether
 * it turns out hidden, masked, or mapped normally.
 *
 * Same sharing rationale as [resolveViewPrivacyOverrides]/[isViewHiddenForSessionReplay]:
 * [TreeViewTraversal] already does this for the default pipeline; [CompositionTreeBuilder] never
 * did, silently ignoring every per-view touch privacy override for as long as pixel capture was
 * enabled.
 */
internal fun resolveTouchPrivacyOverride(
    view: View,
    mappingContext: MappingContext,
    internalLogger: InternalLogger
) {
    val touchPrivacy = view.getTag(R.id.datadog_touch_privacy) ?: return

    val locationOnScreen = IntArray(2)

    // this will always have size >= 2
    @Suppress("UnsafeThirdPartyFunctionCall")
    view.getLocationOnScreen(locationOnScreen)

    val x = locationOnScreen[0]
    val y = locationOnScreen[1]
    val viewArea = Rect(
        x - view.paddingLeft,
        y - view.paddingTop,
        x + view.width + view.paddingRight,
        y + view.height + view.paddingBottom
    )

    try {
        val privacyLevel = TouchPrivacy.valueOf(touchPrivacy.toString().uppercase(Locale.US))
        mappingContext.touchPrivacyManager.addTouchOverrideArea(viewArea, privacyLevel)
    } catch (e: IllegalArgumentException) {
        logInvalidPrivacyLevelError(internalLogger, e)
    }
}

private fun logInvalidPrivacyLevelError(internalLogger: InternalLogger, e: Exception) {
    internalLogger.log(
        InternalLogger.Level.ERROR,
        listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
        { SnapshotProducer.INVALID_PRIVACY_LEVEL_ERROR },
        e
    )
}
