/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.reflection

import androidx.compose.ui.text.MultiParagraph
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import java.lang.reflect.Field
import java.lang.reflect.Method

@Suppress("StringLiteralDuplication")
internal object ComposeReflection {
    val WrappedCompositionClass = getClassSafe("androidx.compose.ui.platform.WrappedComposition")

    val AbstractComposeViewClass = getClassSafe("androidx.compose.ui.platform.AbstractComposeView")
    val CompositionField = AbstractComposeViewClass?.getDeclaredFieldSafe("composition")

    val OwnerField = WrappedCompositionClass?.getDeclaredFieldSafe("owner")

    val LayoutNodeClass = getClassSafe("androidx.compose.ui.node.LayoutNode")
    val GetInteropViewMethod = LayoutNodeClass?.getDeclaredMethodSafe("getInteropView")
    val NodesFieldOfLayoutNode = LayoutNodeClass?.getDeclaredFieldSafe("nodes")
    val NodeChainClass = getClassSafe("androidx.compose.ui.node.NodeChain")
    val HeadFieldOfNodeChain = NodeChainClass?.getDeclaredFieldSafe("head")
    val ModifierNodeClass = getClassSafe("androidx.compose.ui.Modifier\$Node")
    val ChildFieldOfModifierNode = ModifierNodeClass?.getDeclaredFieldSafe("child")

    val SemanticsNodeClass = getClassSafe("androidx.compose.ui.semantics.SemanticsNode")
    val LayoutNodeField = SemanticsNodeClass?.getDeclaredFieldSafe("layoutNode")

    val GetInnerLayerCoordinatorMethod = LayoutNodeClass?.getDeclaredMethodSafe(
        "getInnerLayerCoordinator",
        isCritical = false
    )

    val AndroidComposeViewClass = getClassSafe("androidx.compose.ui.platform.AndroidComposeView")
    val SemanticsOwner = AndroidComposeViewClass?.getDeclaredFieldSafe("semanticsOwner")

    val TextStringSimpleElement = getClassSafe("androidx.compose.foundation.text.modifiers.TextStringSimpleElement")
    val ColorProducerField = TextStringSimpleElement?.getDeclaredFieldSafe("color")

    val BackgroundElementClass = getClassSafe("androidx.compose.foundation.BackgroundElement")
    val ColorField = BackgroundElementClass?.getDeclaredFieldSafe("color")
    val ShapeField = BackgroundElementClass?.getDeclaredFieldSafe("shape")

    val DrawBehindElementClass = getClassSafe("androidx.compose.ui.draw.DrawBehindElement")
    val CheckboxKtClass = getClassSafe("androidx.compose.material.CheckboxKt\$CheckboxImpl\$1\$1", false)
    val RadioButtonKtClass = getClassSafe("androidx.compose.material.RadioButtonKt\$RadioButton\$2\$1", false)
    val CheckDrawingCacheClass = getClassSafe("androidx.compose.material.CheckDrawingCache")

    val BorderColorField = CheckboxKtClass?.getDeclaredFieldSafe("\$borderColor\$delegate")
    val BoxColorField = CheckboxKtClass?.getDeclaredFieldSafe("\$boxColor\$delegate")
    val CheckCacheField = CheckboxKtClass?.getDeclaredFieldSafe("\$checkCache")
    val CheckColorField = CheckboxKtClass?.getDeclaredFieldSafe("\$checkColor\$delegate")
    val CheckPathField = CheckDrawingCacheClass?.getDeclaredFieldSafe("checkPath")
    val OnDrawField = DrawBehindElementClass?.getDeclaredFieldSafe("onDraw")
    val RadioColorField = RadioButtonKtClass?.getDeclaredFieldSafe("\$radioColor")

    val PaddingElementClass = getClassSafe("androidx.compose.foundation.layout.PaddingElement")
    val StartField = PaddingElementClass?.getDeclaredFieldSafe("start")
    val EndField = PaddingElementClass?.getDeclaredFieldSafe("end")
    val BottomField = PaddingElementClass?.getDeclaredFieldSafe("bottom")
    val TopField = PaddingElementClass?.getDeclaredFieldSafe("top")

    val GraphicsLayerElementClass = getClassSafe("androidx.compose.ui.graphics.GraphicsLayerElement")
    val ClipShapeField = GraphicsLayerElementClass?.getDeclaredFieldSafe("shape")

    val PainterElementClass = getClassSafe("androidx.compose.ui.draw.PainterElement")
    val PainterField = PainterElementClass?.getDeclaredFieldSafe("painter")

    val VectorPainterClass = getClassSafe("androidx.compose.ui.graphics.vector.VectorPainter")
    val VectorField = VectorPainterClass?.getDeclaredFieldSafe("vector")

    val BitmapPainterClass = getClassSafe("androidx.compose.ui.graphics.painter.BitmapPainter")
    val ImageField = BitmapPainterClass?.getDeclaredFieldSafe("image")

    val VectorComponent = getClassSafe("androidx.compose.ui.graphics.vector.VectorComponent")
    val CacheDrawScopeField = VectorComponent?.getDeclaredFieldSafe("cacheDrawScope")

    val DrawCacheClass = getClassSafe("androidx.compose.ui.graphics.vector.DrawCache")
    val CachedImageField = DrawCacheClass?.getDeclaredFieldSafe("mCachedImage")

    val AndroidImageBitmapClass = getClassSafe("androidx.compose.ui.graphics.AndroidImageBitmap")
    val BitmapField = AndroidImageBitmapClass?.getDeclaredFieldSafe("bitmap")

    // Region of Coil

    val ContentPainterModifierClass = getClassSafe(
        "coil.compose.ContentPainterModifier",
        isCritical = false
    )
    val PainterFieldOfContentPainterModifier =
        ContentPainterModifierClass?.getDeclaredFieldSafe(
            "painter",
            isCritical = false
        )

    val ContentPainterElementClass = getClassSafe(
        "coil.compose.ContentPainterElement",
        isCritical = false
    )
    val PainterFieldOfContentPainterElement =
        ContentPainterElementClass?.getDeclaredFieldSafe(
            "painter",
            isCritical = false
        )

    val AsyncImagePainterClass = getClassSafe(
        "coil.compose.AsyncImagePainter",
        isCritical = false
    )
    val PainterFieldOfAsyncImagePainter = AsyncImagePainterClass?.getDeclaredFieldSafe("_painter")

    // End region of Coil

    // Region of Coil3

    val PainterNodeClass = getClassSafe(
        "coil3.compose.internal.ContentPainterNode",
        isCritical = false
    )

    val PainterFieldOfPainterNode = PainterNodeClass?.getDeclaredFieldSafe(
        "painter",
        isCritical = false
    )

    val AsyncImagePainter3Class = getClassSafe(
        "coil3.compose.AsyncImagePainter",
        isCritical = false
    )
    val PainterMethodOfAsync3ImagePainter =
        AsyncImagePainter3Class?.getDeclaredMethodSafe(
            "getPainter",
            isCritical = false
        )

    // End region of Coil3

    // Region of MultiParagraph text
    val ParagraphInfoListField = MultiParagraph::class.java.getDeclaredFieldSafe(
        "paragraphInfoList",
        isCritical = false
    )
    val ParagraphInfoClass = getClassSafe("androidx.compose.ui.text.ParagraphInfo")
    val ParagraphField = ParagraphInfoClass?.getDeclaredFieldSafe(
        "paragraph",
        isCritical = false
    )
    val AndroidParagraphClass = getClassSafe("androidx.compose.ui.text.AndroidParagraph")
    val LayoutField = AndroidParagraphClass?.getDeclaredFieldSafe(
        "layout",
        isCritical = false
    )
    val TextLayoutClass = getClassSafe("androidx.compose.ui.text.android.TextLayout")
    val StaticLayoutField = TextLayoutClass?.getDeclaredFieldSafe(
        "layout",
        isCritical = false
    )
}

internal fun Field.accessible(): Field {
    isAccessible = true
    return this
}

internal fun Method.accessible(): Method {
    isAccessible = true
    return this
}

@Suppress("TooGenericExceptionCaught")
internal fun Field.getSafe(target: Any?): Any? {
    return try {
        get(target)
    } catch (e: IllegalAccessException) {
        logReflectionException(name, LOG_TYPE_FIELD, LOG_REASON_FIELD_NO_ACCESSIBLE, e)
        null
    } catch (e: IllegalArgumentException) {
        logReflectionException(name, LOG_TYPE_FIELD, LOG_REASON_INCOMPATIBLE_TYPE, e)
        null
    } catch (e: NullPointerException) {
        logNullPointerException(name, LOG_TYPE_FIELD, e)
        null
    } catch (e: ExceptionInInitializerError) {
        logReflectionException(name, LOG_TYPE_FIELD, LOG_REASON_INITIALIZATION_ERROR, e)
        null
    }
}

internal fun getClassSafe(className: String, isCritical: Boolean = true): Class<*>? {
    return try {
        Class.forName(className)
    } catch (e: LinkageError) {
        if (isCritical) {
            logReflectionException(className, LOG_TYPE_CLASS, LOG_REASON_LINKAGE_ERROR, e)
        }
        null
    } catch (e: ExceptionInInitializerError) {
        if (isCritical) {
            logReflectionException(className, LOG_TYPE_CLASS, LOG_REASON_INITIALIZATION_ERROR, e)
        }
        null
    } catch (e: ClassNotFoundException) {
        if (isCritical) {
            logNoSuchException(className, LOG_TYPE_CLASS, e)
        }
        null
    }
}

@Suppress("TooGenericExceptionCaught")
internal fun Class<*>.getDeclaredFieldSafe(fieldName: String, isCritical: Boolean = true): Field? {
    return try {
        getDeclaredField(fieldName).accessible()
    } catch (e: SecurityException) {
        if (isCritical) {
            logSecurityException(fieldName, LOG_TYPE_FIELD, e)
        }
        null
    } catch (e: NullPointerException) {
        if (isCritical) {
            logNullPointerException(fieldName, LOG_TYPE_FIELD, e)
        }
        null
    } catch (e: NoSuchFieldException) {
        if (isCritical) {
            logNoSuchException(fieldName, LOG_TYPE_FIELD, e)
        }
        null
    }
}

@Suppress("TooGenericExceptionCaught")
internal fun Class<*>.getDeclaredMethodSafe(
    methodName: String,
    isCritical: Boolean = true
): Method? {
    return try {
        getDeclaredMethod(methodName).accessible()
    } catch (e: SecurityException) {
        if (isCritical) {
            logSecurityException(methodName, LOG_TYPE_METHOD, e)
        }
        null
    } catch (e: NullPointerException) {
        if (isCritical) {
            logNullPointerException(methodName, LOG_TYPE_METHOD, e)
        }
        null
    } catch (e: NoSuchMethodException) {
        if (isCritical) {
            logNoSuchException(methodName, LOG_TYPE_METHOD, e)
        }
        null
    }
}

private fun logSecurityException(name: String, type: String, e: SecurityException) {
    logReflectionException(name = name, type = type, reason = LOG_REASON_SECURITY, e = e)
}

private fun logNullPointerException(name: String, type: String, e: NullPointerException) {
    logReflectionException(name = name, type = type, reason = "$name is null", e = e)
}

private fun logNoSuchException(name: String, type: String, e: ReflectiveOperationException) {
    logReflectionException(name = name, type = type, reason = LOG_REASON_DEFAULT, e = e)
}

private fun logReflectionException(name: String, type: String, reason: String, e: Throwable) {
    (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
        level = InternalLogger.Level.ERROR,
        targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
        messageBuilder = { "Unable to get $type [$name] through reflection: $reason" },
        throwable = e,
        onlyOnce = true,
        additionalProperties = mapOf(
            "reflection.type" to type,
            "reflection.name" to name
        )
    )
}

private const val LOG_TYPE_METHOD = "Method"
private const val LOG_TYPE_FIELD = "Field"
private const val LOG_TYPE_CLASS = "Class"
private const val LOG_REASON_FIELD_NO_ACCESSIBLE = "Field is not accessible"
private const val LOG_REASON_INCOMPATIBLE_TYPE = "Target has incompatible type"
private const val LOG_REASON_DEFAULT =
    "Either because of obfuscation or dependency version mismatch"
private const val LOG_REASON_SECURITY = "Security exception"
private const val LOG_REASON_LINKAGE_ERROR = "Linkage error"
private const val LOG_REASON_INITIALIZATION_ERROR = "Error in initialization"
