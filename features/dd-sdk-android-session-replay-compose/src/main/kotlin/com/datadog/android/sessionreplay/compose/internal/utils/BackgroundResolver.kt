/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils.Companion.COLOR_UNSPECIFIED
import com.datadog.android.sessionreplay.utils.GlobalBounds

internal class BackgroundResolver(
    private val reflectionUtils: ReflectionUtils,
    private val innerBoundsOf: (SemanticsNode) -> GlobalBounds,
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) {
    // Diagnostic-logging-only support — see debugLogOnce. A node's background-resolution outcome
    // doesn't change cycle-to-cycle for a static node, so without this every one of these lines
    // would repeat identically on every single decompose() call this class's own caller
    // (DefaultComposeCompositionWalker) makes many times per second on a busy screen.
    private val debugLogExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val loggedOnceMessages: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun debugLogOnce(message: String) {
        if (loggedOnceMessages.add(message)) {
            debugLogExecutor.execute {
                android.util.Log.d(DEBUG_LOG_TAG, "[BackgroundResolver] $message")
            }
        }
    }
    internal fun resolveBackgroundInfo(semanticsNode: SemanticsNode): List<BackgroundInfo> {
        val backgroundInfoList = mutableListOf<BackgroundInfo>()
        // CurrentBackgroundInfo is to store bounds, color and shape information in sequence of modifiers.
        var currentBackgroundInfo = BackgroundInfo()
        var currentBounds: GlobalBounds = resolveOuterBounds(semanticsNode)
        // If the currentBounds is already invalid, return with the existing wireframes
        if (currentBounds.width <= 0 || currentBounds.height <= 0) {
            return backgroundInfoList
        }
        val density = semanticsNode.layoutInfo.density
        // Iterate all the modifiers in user calling sequence, when meet:
        // -> clip(): calculate the corner radius and update `currentBackgroundInfo`
        // -> padding(): shrink the bounds from the previous bounds and update `currentBackgroundInfo`
        // -> background(): retrieve the color and use `currentBackgroundInfo` to generate wireframes,
        //                  then reset `currentBackgroundInfo`.
        semanticsNode.layoutInfo.getModifierInfo().forEach { modifierInfo ->
            if (reflectionUtils.isBackgroundElement(modifierInfo.modifier)) {
                val color = resolveBackgroundElementColor(modifierInfo.modifier)
                currentBackgroundInfo = currentBackgroundInfo.copy(globalBounds = currentBounds, color = color)
                backgroundInfoList.add(currentBackgroundInfo)
                currentBackgroundInfo = BackgroundInfo()
            } else if (reflectionUtils.isPaddingElement(modifierInfo.modifier)) {
                currentBounds = shrinkBounds(modifierInfo.modifier, currentBounds)
                currentBackgroundInfo = currentBackgroundInfo.copy(globalBounds = currentBounds)
            } else if (reflectionUtils.isGraphicsLayerElement(modifierInfo.modifier)) {
                val cornerRadius = reflectionUtils.getClipShape(modifierInfo.modifier)
                    ?.let { resolveCornerRadius(it, currentBounds, density) } ?: 0f
                currentBackgroundInfo = currentBackgroundInfo.copy(cornerRadius = cornerRadius)
            }
        }
        return backgroundInfoList
    }

    internal fun resolveBackgroundColor(semanticsNode: SemanticsNode): Long? {
        val topmostBackground =
            semanticsNode.layoutInfo.getModifierInfo().lastOrNull { modifierInfo ->
                reflectionUtils.isBackgroundElement(modifierInfo.modifier)
            }
        if (topmostBackground == null) {
            // Deliberately kept in (not removed after investigation) at the user's explicit
            // request — see the git history/PR discussion for the "full screen broken images"
            // investigation this was added for. android.util.Log rather than InternalLogger:
            // this is meant to be read straight off Logcat on a real device/app while
            // reproducing the issue, not routed through the SDK's own telemetry/user-facing
            // channels. Distinguishes "no BackgroundElement at all" (a real Material
            // Surface/Card almost certainly paints its background through its own internal
            // implementation, not the public Modifier.background() extension this class reads)
            // from "found one but couldn't resolve a color from it" (see the log inside
            // resolveBackgroundElementColor for that case).
            debugLogOnce(
                "resolveBackgroundColor: node=${semanticsNode.id} " +
                    "no BackgroundElement modifier found at all"
            )
            return null
        }
        val color = resolveBackgroundElementColor(topmostBackground.modifier)
        if (color == null) {
            debugLogOnce(
                "resolveBackgroundColor: node=${semanticsNode.id} " +
                    "BackgroundElement found but resolveBackgroundElementColor returned null"
            )
        }
        return color
    }

    internal fun resolveBackgroundShape(semanticsNode: SemanticsNode): Shape? {
        val backgroundModifier = semanticsNode.layoutInfo.getModifierInfo().lastOrNull {
            reflectionUtils.isBackgroundElement(it.modifier)
        }?.modifier
        return backgroundModifier?.let { reflectionUtils.getShape(it) }
    }

    internal fun resolveCornerRadius(shape: Shape, currentBounds: GlobalBounds, density: Density): Float {
        val size = Size(
            currentBounds.width.toFloat() * density.density,
            currentBounds.height.toFloat() * density.density
        )
        // We only have a single value for corner radius, so we default to using the
        // top left (i.e.: topStart) corner's value and apply it to all corners
        return if (shape is RoundedCornerShape) {
            shape.topStart.toPx(size, density) / density.density
        } else {
            0f
        }
    }

    private fun resolveOuterBounds(semanticsNode: SemanticsNode): GlobalBounds {
        var currentBounds = innerBoundsOf(semanticsNode)
        semanticsNode.layoutInfo.getModifierInfo().filter {
            reflectionUtils.isPaddingElement(it.modifier)
        }.forEach {
            val top = reflectionUtils.getTopPadding(it.modifier)
            val start = reflectionUtils.getStartPadding(it.modifier)
            val end = reflectionUtils.getEndPadding(it.modifier)
            val bottom = reflectionUtils.getBottomPadding(it.modifier)
            currentBounds = GlobalBounds(
                x = currentBounds.x - start.toLong(),
                y = currentBounds.y - top.toLong(),
                width = currentBounds.width + (end + start).toLong(),
                height = currentBounds.height + (bottom + top).toLong()
            )
        }
        return currentBounds
    }

    private fun shrinkBounds(modifier: Modifier, currentBounds: GlobalBounds): GlobalBounds {
        val top = reflectionUtils.getTopPadding(modifier)
        val start = reflectionUtils.getStartPadding(modifier)
        val end = reflectionUtils.getEndPadding(modifier)
        val bottom = reflectionUtils.getBottomPadding(modifier)
        return GlobalBounds(
            x = currentBounds.x + start.toLong(),
            y = currentBounds.y + top.toLong(),
            width = currentBounds.width - (end + start).toLong(),
            height = currentBounds.height - (bottom + top).toLong()
        )
    }

    private fun resolveBackgroundElementColor(modifier: Modifier): Long? {
        val rawColor = reflectionUtils.getColor(modifier)
        val alpha = reflectionUtils.getAlpha(modifier)
        if (rawColor != null && rawColor != COLOR_UNSPECIFIED) {
            return applyAlphaToColor(rawColor, alpha)
        }
        // Deliberately kept in (not removed after investigation) — see resolveBackgroundColor's
        // doc for why this whole file is being instrumented. rawColor being null/unspecified here
        // means the BackgroundElement's `color` param wasn't set directly (or wasn't specified) —
        // the common idiom for that is Modifier.background(brush = ...) instead, handed off to
        // resolveBrushColor below; logging this transition point so a "no color at all" outcome
        // can be told apart from "there was a color parameter, it's just COLOR_UNSPECIFIED".
        debugLogOnce(
            "resolveBackgroundElementColor: rawColor=$rawColor " +
                "(null-or-unspecified), falling through to resolveBrushColor"
        )
        return resolveBrushColor(modifier, alpha)
    }

    private fun resolveBrushColor(modifier: Modifier, alpha: Float?): Long? {
        val brush = reflectionUtils.getBrush(modifier)
        if (brush == null) {
            debugLogOnce("resolveBrushColor: getBrush(modifier) is null")
            return null
        }
        val colors = reflectionUtils.getBrushColors(brush)
        return when {
            colors == null -> {
                debugLogOnce("resolveBrushColor: unsupported Brush type ${brush.javaClass.name}")
                logBrushIssue(brush, InternalLogger.Level.INFO) {
                    "Unsupported Brush type for Compose background: ${brush.javaClass.name}"
                }
                null
            }
            colors.isEmpty() -> {
                debugLogOnce(
                    "resolveBrushColor: known Brush type ${brush.javaClass.name} " +
                        "but reflection read an empty color list"
                )
                logBrushIssue(brush, InternalLogger.Level.WARN) {
                    "Known Brush type but failed to read color list via reflection: ${brush.javaClass.name}"
                }
                null
            }
            else -> {
                @Suppress("UnsafeThirdPartyFunctionCall")
                applyAlphaToColor(colors.first().value.toLong(), alpha)
            }
        }
    }

    // alpha <= 0f is treated as invisible: returns COLOR_UNSPECIFIED so convertColor produces null fill
    private fun applyAlphaToColor(colorValue: Long, alpha: Float?): Long {
        return when {
            alpha == null || alpha >= 1f -> colorValue
            alpha <= 0f -> COLOR_UNSPECIFIED
            else -> {
                val color = Color(colorValue.toULong())
                color.copy(alpha = color.alpha * alpha).value.toLong()
            }
        }
    }

    private fun logBrushIssue(brush: Any, level: InternalLogger.Level, messageBuilder: () -> String) {
        internalLogger.log(
            level = level,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            messageBuilder = messageBuilder,
            onlyOnce = true,
            additionalProperties = mapOf("brush.type" to brush.javaClass.name, COMPONENT_KEY to COMPONENT_NAME)
        )
    }

    private companion object {
        private const val COMPONENT_NAME = "BackgroundResolver"
        private const val COMPONENT_KEY = "component"

        // Shared literally (not a cross-file constant) with DefaultComposeCompositionWalker.kt's
        // own debug logging added for the same "full screen broken images" investigation — kept
        // as a plain string in each file rather than introducing a shared dependency just for a
        // log tag.
        private const val DEBUG_LOG_TAG = "DD_SessionReplay"
    }
}
