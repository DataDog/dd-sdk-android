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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.text.MultiParagraph
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.AsyncImagePainterClass
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.BitmapField
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.ChildFieldOfModifierNode
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.CompositionField
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.ContentPainterElementClass
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.ContentPainterModifierClass
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.GetInnerLayerCoordinatorMethod
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.GetInteropViewMethod
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.HeadFieldOfNodeChain
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.ImageField
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.LayoutField
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.LayoutNodeField
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.NodesFieldOfLayoutNode
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.PainterElementClass
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.PainterField
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.PainterFieldOfAsyncImagePainter
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.PainterFieldOfContentPainterElement
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.PainterFieldOfContentPainterModifier
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.PainterFieldOfPainterNode
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.PainterMethodOfAsync3ImagePainter
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.PainterNodeClass
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.StaticLayoutField
import com.datadog.android.sessionreplay.compose.internal.reflection.getSafe

@Suppress("TooManyFunctions")
internal class ReflectionUtils {

    fun getComposition(view: View): Composition? {
        return CompositionField?.getSafe(view) as? Composition
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
        val layoutNode = LayoutNodeField?.getSafe(semanticsNode)
        val innerLayerCoordinator = layoutNode?.let { GetInnerLayerCoordinatorMethod?.invoke(it) }
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

    fun getBitmapInVectorPainter(vectorPainter: VectorPainter): Bitmap? {
        val vector = ComposeReflection.VectorField?.getSafe(vectorPainter)
        val cacheDrawScope = ComposeReflection.CacheDrawScopeField?.getSafe(vector)
        val mCachedImage = ComposeReflection.CachedImageField?.getSafe(cacheDrawScope)
        return mCachedImage?.let {
            BitmapField?.getSafe(it) as? Bitmap
        }
    }

    fun getBitmapInBitmapPainter(bitmapPainter: BitmapPainter): Bitmap? {
        return ImageField?.getSafe(bitmapPainter)?.let { image ->
            BitmapField?.getSafe(image) as? Bitmap
        }
    }

    fun getCoil3AsyncImagePainter(semanticsNode: SemanticsNode): Painter? {
        // Check if Coil3 ContentPainterNode is present first to optimize the performance
        // by skipping the node chain iteration
        if (PainterNodeClass == null) {
            return null
        }
        val layoutNode = LayoutNodeField?.getSafe(semanticsNode)
        val nodeChain = NodesFieldOfLayoutNode?.getSafe(layoutNode)
        val headNode = HeadFieldOfNodeChain?.getSafe(nodeChain) as? Modifier.Node
        var currentNode = headNode
        var painterNode: Modifier.Node? = null
        // Iterate NodeChain to find Coil3 `ContentPainterNode`
        while (currentNode != null) {
            if (currentNode::class.java == PainterNodeClass) {
                painterNode = currentNode
                break
            }
            currentNode = ChildFieldOfModifierNode?.getSafe(currentNode) as? Modifier.Node
        }
        val asyncImagePainter = PainterFieldOfPainterNode?.getSafe(painterNode)
        val painter =
            asyncImagePainter?.let { PainterMethodOfAsync3ImagePainter?.invoke(it) }
        return painter as? Painter
    }

    fun getLocalImagePainter(semanticsNode: SemanticsNode): Painter? {
        val modifier = semanticsNode.layoutInfo.getModifierInfo().firstOrNull {
            PainterElementClass?.isInstance(it.modifier) == true
        }?.modifier
        return modifier?.let { PainterField?.getSafe(it) as? Painter }
    }

    fun getAsyncImagePainter(semanticsNode: SemanticsNode): Painter? {
        // Check if Coil AsyncImagePainter is present first to optimize the performance
        // by skipping the modifier iteration
        if (AsyncImagePainterClass == null) {
            return null
        }
        val asyncPainter = semanticsNode.layoutInfo.getModifierInfo().firstNotNullOfOrNull {
            if (ContentPainterModifierClass?.isInstance(it.modifier) == true) {
                PainterFieldOfContentPainterModifier?.getSafe(it.modifier)
            } else if (ContentPainterElementClass?.isInstance(it.modifier) == true) {
                PainterFieldOfContentPainterElement?.getSafe(it.modifier)
            } else {
                null
            }
        }
        return PainterFieldOfAsyncImagePainter?.getSafe(asyncPainter) as? Painter
    }

    fun getNestedPainter(painter: Painter): Painter? {
        return PainterFieldOfAsyncImagePainter?.getSafe(painter) as? Painter
    }

    fun getMultiParagraphCapturedText(multiParagraph: MultiParagraph): String? {
        val infoList = ComposeReflection.ParagraphInfoListField?.getSafe(multiParagraph) as? List<*>
        val paragraphInfo = infoList?.firstOrNull()
        val paragraph = ComposeReflection.ParagraphField?.getSafe(paragraphInfo)
        val layout = LayoutField?.getSafe(paragraph)
        val staticLayout = StaticLayoutField?.getSafe(layout) as? StaticLayout
        return staticLayout?.text?.toString()
    }

    fun getInteropView(semanticsNode: SemanticsNode): View? {
        return GetInteropViewMethod?.invoke(semanticsNode.layoutInfo) as? View
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
