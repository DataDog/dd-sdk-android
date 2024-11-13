/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.reflection

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import java.lang.reflect.Field
import java.lang.reflect.Method

internal object ComposeReflection {
    val WrappedCompositionClass = getClassSafe("androidx.compose.ui.platform.WrappedComposition")

    val AbstractComposeViewClass = getClassSafe("androidx.compose.ui.platform.AbstractComposeView")
    val CompositionField = AbstractComposeViewClass?.getDeclaredFieldSafe("composition")

    val OwnerField = WrappedCompositionClass?.getDeclaredFieldSafe("owner")

    val LayoutNodeClass = getClassSafe("androidx.compose.ui.node.LayoutNode")

    val SemanticsNodeClass = getClassSafe("androidx.compose.ui.semantics.SemanticsNode")
    val LayoutNodeField = SemanticsNodeClass?.getDeclaredFieldSafe("layoutNode")

    val GetInnerLayerCoordinatorMethod = LayoutNodeClass?.getDeclaredMethodSafe("getInnerLayerCoordinator")

    val AndroidComposeViewClass = getClassSafe("androidx.compose.ui.platform.AndroidComposeView")
    val SemanticsOwner = AndroidComposeViewClass?.getDeclaredFieldSafe("semanticsOwner")

    val TextStringSimpleElement = getClassSafe("androidx.compose.foundation.text.modifiers.TextStringSimpleElement")
    val ColorProducerField = TextStringSimpleElement?.getDeclaredFieldSafe("color")

    val BackgroundElementClass = getClassSafe("androidx.compose.foundation.BackgroundElement")
    val ColorField = BackgroundElementClass?.getDeclaredFieldSafe("color")

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

    val ContentPainterModifierClass = getClassSafe("coil.compose.ContentPainterModifier")
    val PainterFieldOfContentPainter = ContentPainterModifierClass?.getDeclaredFieldSafe("painter")

    val AsyncImagePainterClass = getClassSafe("coil.compose.AsyncImagePainter")
    val PainterFieldOfAsyncImagePainter = AsyncImagePainterClass?.getDeclaredFieldSafe("_painter")
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
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Unable to get field $name on $target through reflection, field is not accessible" },
            e
        )
        null
    } catch (e: IllegalArgumentException) {
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Unable to get field $name on $target through reflection, target has incompatible type" },
            e
        )
        null
    } catch (e: NullPointerException) {
        logNullPointerException(name, LOG_TYPE_FIELD, e)
        null
    } catch (e: ExceptionInInitializerError) {
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Unable to get field $name on $target through reflection, initialization error" },
            e
        )
        null
    }
}

internal fun getClassSafe(className: String): Class<*>? {
    return try {
        Class.forName(className)
    } catch (e: LinkageError) {
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Unable to get class $className through reflection" },
            e
        )
        null
    } catch (e: ExceptionInInitializerError) {
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Unable to get class $className through reflection, error in Class initialization" },
            e
        )
        null
    } catch (e: ClassNotFoundException) {
        logNoSuchException(className, LOG_TYPE_CLASS, e)
        null
    }
}

@Suppress("TooGenericExceptionCaught")
internal fun Class<*>.getDeclaredFieldSafe(fieldName: String): Field? {
    return try {
        getDeclaredField(fieldName).accessible()
    } catch (e: SecurityException) {
        logSecurityException(fieldName, LOG_TYPE_FIELD, e)
        null
    } catch (e: NullPointerException) {
        logNullPointerException(fieldName, LOG_TYPE_FIELD, e)
        null
    } catch (e: NoSuchFieldException) {
        logNoSuchException(fieldName, LOG_TYPE_FIELD, e)
        null
    }
}

@Suppress("TooGenericExceptionCaught")
internal fun Class<*>.getDeclaredMethodSafe(methodName: String): Method? {
    return try {
        getDeclaredMethod(methodName).accessible()
    } catch (e: SecurityException) {
        logSecurityException(methodName, LOG_TYPE_METHOD, e)
        null
    } catch (e: NullPointerException) {
        logNullPointerException(methodName, LOG_TYPE_METHOD, e)
        null
    } catch (e: NoSuchMethodException) {
        logNoSuchException(methodName, LOG_TYPE_METHOD, e)
        null
    }
}

private fun logSecurityException(name: String, type: String, e: SecurityException) {
    (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
        InternalLogger.Level.ERROR,
        InternalLogger.Target.MAINTAINER,
        {
            "Unable to get $type $name through reflection"
        },
        e
    )
}

private fun logNullPointerException(name: String, type: String, e: NullPointerException) {
    (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
        InternalLogger.Level.ERROR,
        InternalLogger.Target.MAINTAINER,
        {
            "Unable to get $type $name through reflection, name is null"
        },
        e
    )
}

private fun logNoSuchException(name: String, type: String, e: ReflectiveOperationException) {
    (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
        InternalLogger.Level.ERROR,
        InternalLogger.Target.MAINTAINER,
        {
            "Unable to get $type $name through reflection, " +
                "either because of obfuscation or dependency version mismatch"
        },
        e
    )
}

private const val LOG_TYPE_METHOD = "method"
private const val LOG_TYPE_FIELD = "field"
private const val LOG_TYPE_CLASS = "field"
