/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.sessionreplay.compose.internal.granular

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BuildDrawCacheParams
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.datadog.android.internal.sessionreplay.composition.CapturedBackgroundGradient
import com.datadog.android.internal.sessionreplay.composition.CapturedGradientStop
import com.datadog.android.internal.sessionreplay.composition.CapturedPoint
import com.datadog.android.internal.sessionreplay.composition.CapturedShapeBorder
import com.datadog.android.internal.sessionreplay.composition.CapturedShapeStyle
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal data class ComposeDrawingRect(
    val position: Offset,
    val size: Size,
    val style: CapturedShapeStyle,
    val border: CapturedShapeBorder? = null
)
internal data class ResolvedComposeDrawing(val before: List<ComposeDrawingRect>, val after: List<ComposeDrawingRect>)

internal interface ComposeDrawingResolver {
    fun canResolve(modifier: Modifier): Boolean
    fun resolve(node: SemanticsNode, modifier: ModifierInfo): ResolvedComposeDrawing?
}

/** Records supported drawing commands without reading pixels, drawing content, or allocating a bitmap. */
internal class DefaultComposeDrawingResolver : ComposeDrawingResolver {
    private val drawingFields = ConcurrentHashMap<Class<*>, Field>()
    private val reflectionUtils = ReflectionUtils()

    override fun canResolve(modifier: Modifier): Boolean =
        modifier.javaClass.name in DRAWING_CLASSES || isLegacyIndication(modifier)

    @Suppress("DEPRECATION") // Current Material components can still use the legacy indication.
    private fun isLegacyIndication(modifier: Modifier): Boolean = modifier is DrawModifier &&
        modifier.javaClass.name == "androidx.compose.foundation.IndicationModifier"

    @Suppress("ReturnCount", "TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    override fun resolve(node: SemanticsNode, modifier: ModifierInfo): ResolvedComposeDrawing? {
        if (!canResolve(modifier.modifier) || !modifier.coordinates.isAttached) return null
        val dimensions = modifier.coordinates.size
        return try {
            val density = node.layoutInfo.density
            val direction = node.layoutInfo.layoutDirection
            val size = Size(dimensions.width.toFloat(), dimensions.height.toFloat())
            val drawing = drawingFor(modifier.modifier, density, direction, size) ?: return null
            val base = CanvasDrawScope().apply {
                drawParams.density = density
                drawParams.layoutDirection = direction
                drawParams.size = size
            }
            val recorder = RectangleScope(base)
            drawing(recorder)
            if (recorder.contentCalls == 1) ResolvedComposeDrawing(recorder.before, recorder.after) else null
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST", "ReturnCount", "DEPRECATION", "UnsafeThirdPartyFunctionCall")
    internal fun drawingFor(
        modifier: Modifier,
        density: Density,
        direction: LayoutDirection,
        size: Size
    ): (ContentDrawScope.() -> Unit)? {
        if (isLegacyIndication(modifier)) return { with(modifier as DrawModifier) { draw() } }
        val type = modifier.javaClass
        val fieldName = if (type.name.endsWith("DrawWithCacheElement")) "onBuildDrawCache" else "onDraw"
        val field = drawingFields.getOrPut(type) { type.getDeclaredField(fieldName).apply { isAccessible = true } }
        val callback = field.get(modifier)
        return when (type.name) {
            "androidx.compose.ui.draw.DrawBehindElement" -> {
                val block = callback as? (DrawScope.() -> Unit) ?: return null
                val onDraw: ContentDrawScope.() -> Unit = { block(); drawContent() }
                onDraw
            }
            "androidx.compose.ui.draw.DrawWithContentElement" -> callback as? (ContentDrawScope.() -> Unit)
            "androidx.compose.ui.draw.DrawWithCacheElement" -> {
                val build = callback as? (CacheDrawScope.() -> DrawResult) ?: return null
                val cache = CacheDrawScope()
                val parameters = object : BuildDrawCacheParams {
                    override val size: Size = size
                    override val density: Density = density
                    override val layoutDirection: LayoutDirection = direction
                }
                cacheParamsField.set(cache, parameters)
                resultBlockField.get(build(cache)) as? (ContentDrawScope.() -> Unit)
            }
            else -> null
        }
    }

    /** Other draw operations reach CanvasDrawScope's throwing EmptyCanvas and fail closed. */
    @Suppress("UnsafeThirdPartyFunctionCall") // Finite primitive geometry; resolve guards third-party draw callbacks.
    private inner class RectangleScope(private val base: CanvasDrawScope) : ContentDrawScope, DrawScope by base {
        val before = mutableListOf<ComposeDrawingRect>()
        val after = mutableListOf<ComposeDrawingRect>()
        var contentCalls = 0

        override fun drawContent() { contentCalls++ }

        override fun drawRect(
            color: Color,
            topLeft: Offset,
            size: Size,
            alpha: Float,
            style: DrawStyle,
            colorFilter: ColorFilter?,
            blendMode: BlendMode
        ) = addRect(
            topLeft,
            size,
            style,
            colorFilter,
            blendMode,
            CapturedShapeStyle(backgroundColor = format(color, alpha))
        )

        override fun drawRect(
            brush: Brush,
            topLeft: Offset,
            size: Size,
            alpha: Float,
            style: DrawStyle,
            colorFilter: ColorFilter?,
            blendMode: BlendMode
        ) {
            val fill = if (brush is SolidColor) {
                CapturedShapeStyle(backgroundColor = format(brush.value, alpha))
            } else {
                CapturedShapeStyle(backgroundGradient = gradient(brush, topLeft, size, base.size, alpha))
            }
            addRect(topLeft, size, style, colorFilter, blendMode, fill)
        }

        override fun drawRoundRect(
            color: Color,
            topLeft: Offset,
            size: Size,
            cornerRadius: CornerRadius,
            style: DrawStyle,
            alpha: Float,
            colorFilter: ColorFilter?,
            blendMode: BlendMode
        ) {
            if (cornerRadius.x != cornerRadius.y) unsupported()
            addRect(
                topLeft,
                size,
                style,
                colorFilter,
                blendMode,
                CapturedShapeStyle(backgroundColor = format(color, alpha), cornerRadius = cornerRadius.x.toDouble())
            )
        }

        override fun drawRoundRect(
            brush: Brush,
            topLeft: Offset,
            size: Size,
            cornerRadius: CornerRadius,
            alpha: Float,
            style: DrawStyle,
            colorFilter: ColorFilter?,
            blendMode: BlendMode
        ) {
            if (brush !is SolidColor) unsupported()
            drawRoundRect(brush.value, topLeft, size, cornerRadius, style, alpha, colorFilter, blendMode)
        }

        override fun drawCircle(
            color: Color,
            radius: Float,
            center: Offset,
            alpha: Float,
            style: DrawStyle,
            colorFilter: ColorFilter?,
            blendMode: BlendMode
        ) {
            val stroke = style as? Stroke
            val supportedStyle = style == Fill || (stroke != null && stroke.pathEffect == null)
            if (!supportedStyle || colorFilter != null || blendMode != BlendMode.SrcOver) unsupported()
            val thickness = stroke?.width ?: 0f
            if (stroke != null && thickness <= 0f) unsupported()
            val outer = radius + thickness / 2
            if (outer <= 0f) return
            val formatted = format(color, alpha)
            addPrimitive(
                ComposeDrawingRect(
                    Offset(center.x - outer, center.y - outer),
                    Size(outer * 2, outer * 2),
                    CapturedShapeStyle(
                        backgroundColor = formatted.takeIf { stroke == null },
                        cornerRadius = outer.toDouble()
                    ),
                    stroke?.let { CapturedShapeBorder(formatted, thickness.roundToLong().coerceAtLeast(1)) }
                )
            )
        }

        override fun drawLine(
            color: Color,
            start: Offset,
            end: Offset,
            strokeWidth: Float,
            cap: StrokeCap,
            pathEffect: PathEffect?,
            alpha: Float,
            colorFilter: ColorFilter?,
            blendMode: BlendMode
        ) {
            val horizontal = start.y == end.y
            val axisAligned = horizontal || start.x == end.x
            val simpleStroke = strokeWidth > 0f && pathEffect == null
            val simplePaint = colorFilter == null && blendMode == BlendMode.SrcOver
            if (!axisAligned || !simpleStroke || !simplePaint) unsupported()
            val extension = if (cap == StrokeCap.Butt) 0f else strokeWidth / 2
            val left = if (horizontal) min(start.x, end.x) - extension else start.x - strokeWidth / 2
            val top = if (horizontal) start.y - strokeWidth / 2 else min(start.y, end.y) - extension
            val width = if (horizontal) abs(end.x - start.x) + extension * 2 else strokeWidth
            val height = if (horizontal) strokeWidth else abs(end.y - start.y) + extension * 2
            addPrimitive(
                ComposeDrawingRect(
                    Offset(left, top),
                    Size(width, height),
                    CapturedShapeStyle(
                        backgroundColor = format(color, alpha),
                        cornerRadius = if (cap == StrokeCap.Round) (strokeWidth / 2).toDouble() else null
                    )
                )
            )
        }

        private fun addPrimitive(rectangle: ComposeDrawingRect) {
            if (contentCalls > 1) unsupported()
            (if (contentCalls == 0) before else after) += rectangle
        }

        private fun addRect(
            position: Offset,
            size: Size,
            style: DrawStyle,
            filter: ColorFilter?,
            blend: BlendMode,
            fill: CapturedShapeStyle
        ) {
            val simpleFill = style == Fill && filter == null && blend == BlendMode.SrcOver
            if (!simpleFill || contentCalls > 1) unsupported()
            if (size.width > 0 && size.height > 0) {
                addPrimitive(ComposeDrawingRect(position, size, fill))
            }
        }
    }

    @Suppress("UNCHECKED_CAST", "UnsafeThirdPartyFunctionCall") // Known gradient fields; resolve catches failures.
    private fun gradient(
        brush: Brush,
        position: Offset,
        size: Size,
        area: Size,
        alpha: Float
    ): CapturedBackgroundGradient {
        if (brush.javaClass.name != "androidx.compose.ui.graphics.LinearGradient") {
            unsupported()
        }
        val type = brush.javaClass
        fun field(name: String): Any? = type.getDeclaredField(name).apply { isAccessible = true }.get(brush)
        if ((field("tileMode") as Number).toInt() != 0) unsupported()
        val colors = reflectionUtils.getBrushColors(brush) ?: unsupported()
        val stops = field("stops") as? List<Float>
        if (colors.size < 2 || (stops != null && stops.size != colors.size)) unsupported()
        return CapturedBackgroundGradient(
            colors.mapIndexed { index, color ->
                CapturedGradientStop(
                    format(color, alpha),
                    stops?.get(index)?.toDouble() ?: index.toDouble() / (colors.size - 1)
                )
            },
            gradientPoint((field("start") as Number).toLong(), position, size, area),
            gradientPoint((field("end") as Number).toLong(), position, size, area)
        )
    }

    private fun gradientPoint(value: Long, position: Offset, size: Size, area: Size): CapturedPoint {
        val x = Float.fromBits((value shr OFFSET_SHIFT).toInt())
        val y = Float.fromBits(value.toInt())
        val resolvedX = if (x == Float.POSITIVE_INFINITY) area.width else x
        val resolvedY = if (y == Float.POSITIVE_INFINITY) area.height else y
        return CapturedPoint(
            ((resolvedX - position.x) / size.width).toDouble(),
            ((resolvedY - position.y) / size.height).toDouble()
        )
    }

    @Suppress("ThrowingInternalException") // Resolve catches this control-flow signal and keeps semantic content.
    private fun unsupported(): Nothing = throw UnsupportedOperationException("Unsupported drawing operation")

    private fun format(color: Color, alpha: Float): String {
        val resolved = color.copy(alpha = color.alpha * alpha)
        return DefaultColorStringFormatter.formatColorAndAlphaAsHexString(
            resolved.toArgb(),
            (resolved.alpha * COLOR_MAX).roundToInt()
        )
    }

    private companion object {
        val DRAWING_CLASSES = setOf(
            "androidx.compose.ui.draw.DrawBehindElement",
            "androidx.compose.ui.draw.DrawWithContentElement",
            "androidx.compose.ui.draw.DrawWithCacheElement"
        )

        @Suppress("UnsafeThirdPartyFunctionCall") // Lazy lookup is guarded by resolve.
        val cacheParamsField: Field by lazy {
            CacheDrawScope::class.java.getDeclaredField("cacheParams").apply {
                isAccessible =
                    true
            }
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // Lazy lookup is guarded by resolve.
        val resultBlockField: Field by lazy {
            DrawResult::class.java.getDeclaredField("block").apply {
                isAccessible =
                    true
            }
        }
        const val OFFSET_SHIFT = 32
        const val COLOR_MAX = 255
    }
}
