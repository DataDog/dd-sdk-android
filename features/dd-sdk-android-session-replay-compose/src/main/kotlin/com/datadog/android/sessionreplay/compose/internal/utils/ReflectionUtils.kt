/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import android.text.StaticLayout
import android.view.View
import androidx.compose.animation.core.AnimationState
import androidx.compose.runtime.Composition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.text.MultiParagraph
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection
import com.datadog.android.sessionreplay.compose.internal.reflection.getSafe
import java.lang.reflect.InvocationTargetException

@Suppress("TooManyFunctions")
internal class ReflectionUtils {

    fun getComposition(view: View): Composition? {
        return ComposeReflection.CompositionField?.getSafe(view) as? Composition
    }

    fun isBackgroundElement(modifier: Modifier): Boolean {
        return ComposeReflection.BackgroundElementClass?.isInstance(modifier) == true
    }

    fun isPaddingElement(modifier: Modifier): Boolean {
        return ComposeReflection.PaddingElementClass?.isInstance(modifier) == true
    }

    fun isTextStringSimpleElement(modifier: Modifier): Boolean {
        return ComposeReflection.TextStringSimpleElement?.isInstance(modifier) == true
    }

    fun isWrappedCompositionClass(composition: Composition): Boolean {
        return ComposeReflection.WrappedCompositionClass?.isInstance(composition) == true
    }

    fun isAndroidComposeView(any: Any): Boolean {
        return ComposeReflection.AndroidComposeViewClass?.isInstance(any) == true
    }

    fun isAsyncImagePainter(painter: Painter): Boolean {
        return ComposeReflection.AsyncImagePainterClass?.isInstance(painter) == true
    }

    fun isDrawBehindElementClass(modifier: Modifier): Boolean {
        return ComposeReflection.DrawBehindElementClass?.isInstance(modifier) == true
    }

    fun getOwner(composition: Composition): Any? {
        return ComposeReflection.OwnerField?.getSafe(composition)
    }

    fun getSemanticsOwner(any: Any): SemanticsOwner? {
        return ComposeReflection.SemanticsOwner?.getSafe(any) as? SemanticsOwner
    }

    fun isGraphicsLayerElement(modifier: Modifier): Boolean {
        return ComposeReflection.GraphicsLayerElementClass?.isInstance(modifier) == true
    }

    fun getColorProducerColor(modifier: Modifier): Color? {
        return (ComposeReflection.ColorProducerField?.getSafe(modifier) as? ColorProducer)?.invoke()
    }

    fun getTextStringSimpleElementOverflow(modifier: Modifier): Any? {
        return ComposeReflection.TextStringSimpleElementOverflowField?.getSafe(modifier)
    }

    fun getPlaceable(semanticsNode: SemanticsNode): Placeable? {
        val layoutNode = ComposeReflection.LayoutNodeField?.getSafe(semanticsNode)
        val innerLayerCoordinator = layoutNode?.let {
            ComposeReflection.GetInnerLayerCoordinatorMethod?.invoke(it)
        }
        return innerLayerCoordinator as? Placeable
    }

    fun getTopPadding(modifier: Modifier): Float {
        return ComposeReflection.TopField?.getSafe(modifier) as? Float ?: 0.0f
    }

    fun getStartPadding(modifier: Modifier): Float {
        return ComposeReflection.StartField?.getSafe(modifier) as? Float ?: 0.0f
    }

    fun getBottomPadding(modifier: Modifier): Float {
        return ComposeReflection.BottomField?.getSafe(modifier) as? Float ?: 0.0f
    }

    fun getEndPadding(modifier: Modifier): Float {
        return ComposeReflection.EndField?.getSafe(modifier) as? Float ?: 0.0f
    }

    fun getColor(modifier: Modifier): Long? {
        return ComposeReflection.ColorField?.getSafe(modifier) as? Long
    }

    fun getShape(modifier: Modifier): Shape? {
        return ComposeReflection.ShapeField?.getSafe(modifier) as? Shape
    }

    fun getClipShape(modifier: Modifier): Shape? {
        return ComposeReflection.ClipShapeField?.getSafe(modifier) as? Shape
    }

    fun getAlpha(modifier: Modifier): Float? {
        return ComposeReflection.AlphaField?.getSafe(modifier) as? Float
    }

    @Suppress("NestedBlockDepth")
    fun hasAncestorWithAlphaLessThanOne(semanticsNode: SemanticsNode): Boolean {
        var currentLayoutNode = ComposeReflection.LayoutNodeField?.getSafe(semanticsNode)
        while (currentLayoutNode != null) {
            val parentLayoutNode =
                ComposeReflection.ParentOfLayoutNode?.getSafe(currentLayoutNode) ?: break
            val modifierInfoList = invokeGetModifierInfo(parentLayoutNode)
            modifierInfoList?.forEach { modifierInfo ->
                val modifier = getModifierFromModifierInfo(modifierInfo)
                if (modifier != null && isGraphicsLayerElement(modifier)) {
                    val alpha = getAlpha(modifier)
                    if (alpha != null && alpha < 1f) {
                        return true
                    }
                }
            }
            currentLayoutNode = parentLayoutNode
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeGetModifierInfo(layoutNode: Any): List<Any>? {
        return try {
            ComposeReflection.GetModifierInfoMethod?.invoke(layoutNode) as? List<Any>
        } catch (e: IllegalAccessException) {
            logReflectionError("getModifierInfo", "Method", "Method is not accessible", e)
            null
        } catch (e: IllegalArgumentException) {
            logReflectionError("getModifierInfo", "Method", "Incompatible receiver type", e)
            null
        } catch (e: InvocationTargetException) {
            logReflectionError("getModifierInfo", "Method", "Method threw an exception", e)
            null
        }
    }

    @Suppress("ModifierFactoryExtensionFunction")
    private fun getModifierFromModifierInfo(modifierInfo: Any): Modifier? {
        return try {
            modifierInfo.javaClass.getDeclaredField("modifier")
                .apply { isAccessible = true }
                .get(modifierInfo) as? Modifier
        } catch (e: NoSuchFieldException) {
            logReflectionError("modifier", "Field", "Field not found in ModifierInfo", e)
            null
        } catch (e: SecurityException) {
            logReflectionError("modifier", "Field", "Security manager denied access", e)
            null
        } catch (e: IllegalAccessException) {
            logReflectionError("modifier", "Field", "Field is not accessible", e)
            null
        } catch (e: IllegalArgumentException) {
            logReflectionError("modifier", "Field", "Incompatible object type", e)
            null
        }
    }

    private fun logReflectionError(name: String, type: String, reason: String, throwable: Throwable) {
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            messageBuilder = { "Unable to access $type [$name] through reflection: $reason" },
            throwable = throwable,
            onlyOnce = true,
            additionalProperties = mapOf(
                "reflection.type" to type,
                "reflection.name" to name
            )
        )
    }

    fun getBitmapInVectorPainter(vectorPainter: VectorPainter): Bitmap? {
        val vector = ComposeReflection.VectorField?.getSafe(vectorPainter)
        val cacheDrawScope = ComposeReflection.CacheDrawScopeField?.getSafe(vector)
        val mCachedImage = ComposeReflection.CachedImageField?.getSafe(cacheDrawScope)
        return mCachedImage?.let {
            ComposeReflection.BitmapField?.getSafe(it) as? Bitmap
        }
    }

    fun getBitmapInBitmapPainter(bitmapPainter: BitmapPainter): Bitmap? {
        return ComposeReflection.ImageField?.getSafe(bitmapPainter)?.let { image ->
            ComposeReflection.BitmapField?.getSafe(image) as? Bitmap
        }
    }

    fun getCoil3AsyncImagePainter(semanticsNode: SemanticsNode): Painter? {
        // Check if Coil3 ContentPainterNode is present first to optimize the performance
        // by skipping the node chain iteration
        if (ComposeReflection.PainterNodeClass == null) {
            return null
        }
        val layoutNode = ComposeReflection.LayoutNodeField?.getSafe(semanticsNode)
        val nodeChain = ComposeReflection.NodesFieldOfLayoutNode?.getSafe(layoutNode)
        val headNode = ComposeReflection.HeadFieldOfNodeChain?.getSafe(nodeChain) as? Modifier.Node
        var currentNode = headNode
        var painterNode: Modifier.Node? = null
        // Iterate NodeChain to find Coil3 `ContentPainterNode`
        while (currentNode != null) {
            if (currentNode::class.java == ComposeReflection.PainterNodeClass) {
                painterNode = currentNode
                break
            }
            currentNode =
                ComposeReflection.ChildFieldOfModifierNode?.getSafe(currentNode) as? Modifier.Node
        }
        val asyncImagePainter = ComposeReflection.PainterFieldOfPainterNode?.getSafe(painterNode)
        val painter =
            asyncImagePainter?.let { ComposeReflection.PainterMethodOfAsync3ImagePainter?.invoke(it) }
        return painter as? Painter
    }

    fun getLocalImagePainter(semanticsNode: SemanticsNode): Painter? {
        val modifier = semanticsNode.layoutInfo.getModifierInfo().firstOrNull {
            ComposeReflection.PainterElementClass?.isInstance(it.modifier) == true
        }?.modifier
        return modifier?.let { ComposeReflection.PainterField?.getSafe(it) as? Painter }
    }

    fun getContentScale(semanticsNode: SemanticsNode): ContentScale? {
        val modifier = semanticsNode.layoutInfo.getModifierInfo().firstOrNull {
            ComposeReflection.PainterElementClass?.isInstance(it.modifier) == true
        }?.modifier
        return modifier?.let { ComposeReflection.ContentScaleField?.getSafe(it) as? ContentScale }
    }

    fun getAlignment(semanticsNode: SemanticsNode): Alignment? {
        val modifier = semanticsNode.layoutInfo.getModifierInfo().firstOrNull {
            ComposeReflection.PainterElementClass?.isInstance(it.modifier) == true
        }?.modifier
        return modifier?.let { ComposeReflection.AlignmentField?.getSafe(it) as? Alignment }
    }

    fun getAsyncImagePainter(semanticsNode: SemanticsNode): Painter? {
        // Check if Coil AsyncImagePainter is present first to optimize the performance
        // by skipping the modifier iteration
        if (ComposeReflection.AsyncImagePainterClass == null) {
            return null
        }
        val asyncPainter = semanticsNode.layoutInfo.getModifierInfo().firstNotNullOfOrNull {
            if (ComposeReflection.ContentPainterModifierClass?.isInstance(it.modifier) == true) {
                ComposeReflection.PainterFieldOfContentPainterModifier?.getSafe(it.modifier)
            } else if (ComposeReflection.ContentPainterElementClass?.isInstance(it.modifier) == true) {
                ComposeReflection.PainterFieldOfContentPainterElement?.getSafe(it.modifier)
            } else {
                null
            }
        }
        return ComposeReflection.PainterFieldOfAsyncImagePainter?.getSafe(asyncPainter) as? Painter
    }

    fun getNestedPainter(painter: Painter): Painter? {
        return ComposeReflection.PainterFieldOfAsyncImagePainter?.getSafe(painter) as? Painter
    }

    fun getMultiParagraphCapturedText(multiParagraph: MultiParagraph): String? {
        val infoList =
            ComposeReflection.ParagraphInfoListField?.getSafe(multiParagraph) as? List<*>
        val paragraphInfo = infoList?.firstOrNull()
        val paragraph = ComposeReflection.ParagraphField?.getSafe(paragraphInfo)
        val layout = ComposeReflection.LayoutField?.getSafe(paragraph)
        val staticLayout = ComposeReflection.StaticLayoutField?.getSafe(layout) as? StaticLayout
        return staticLayout?.text?.toString()
    }

    fun getInteropView(semanticsNode: SemanticsNode): View? {
        return ComposeReflection.GetInteropViewMethod?.invoke(semanticsNode.layoutInfo) as? View
    }

    fun getOnDraw(modifier: Modifier): Any? {
        return ComposeReflection.OnDrawField?.getSafe(modifier)
    }

    fun getBoxColor(onDrawInstance: Any): AnimationState<*, *>? {
        return ComposeReflection.BoxColorField?.getSafe(onDrawInstance) as? AnimationState<*, *>
    }

    fun getRadioColor(onDrawInstance: Any): AnimationState<*, *>? {
        return ComposeReflection.RadioColorField?.getSafe(onDrawInstance) as? AnimationState<*, *>
    }

    fun getCheckColor(onDrawInstance: Any): AnimationState<*, *>? {
        return ComposeReflection.CheckColorField?.getSafe(onDrawInstance) as? AnimationState<*, *>
    }

    fun getBorderColor(onDrawInstance: Any): AnimationState<*, *>? {
        return ComposeReflection.BorderColorField?.getSafe(onDrawInstance) as? AnimationState<*, *>
    }

    fun getCheckCache(onDrawInstance: Any): Any? {
        return ComposeReflection.CheckCacheField?.getSafe(onDrawInstance)
    }

    fun getCheckPath(checkCache: Any): Path? {
        return ComposeReflection.CheckPathField?.getSafe(checkCache) as? Path
    }
}
