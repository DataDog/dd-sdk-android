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

internal object ComposeReflection {
    val WrappedCompositionClass = getClassSafe("androidx.compose.ui.platform.WrappedComposition")
    val WrappedCompositionOriginalField = WrappedCompositionClass?.getDeclaredFieldSafe("original")

    val CompositionImplClass = getClassSafe("androidx.compose.runtime.CompositionImpl")
    val CompositionImplComposerField = CompositionImplClass?.getDeclaredFieldSafe("composer")

    val CompositionContextHolderClass = getClassSafe("androidx.compose.runtime.ComposerImpl\$CompositionContextHolder")
    val CompositionContextHolderRefField = CompositionContextHolderClass?.getDeclaredFieldSafe("ref")

    val CompositionContextImplClass = getClassSafe("androidx.compose.runtime.ComposerImpl\$CompositionContextImpl")
    val CompositionContextImplComposersField = CompositionContextImplClass?.getDeclaredFieldSafe("composers")

    val AnchorClass = getClassSafe("androidx.compose.runtime.Anchor")
    val AnchorLocationField = AnchorClass?.getDeclaredFieldSafe("location")

    val RecomposeScopeImplClass = getClassSafe("androidx.compose.runtime.RecomposeScopeImpl")
    val RecomposeScopeImplBlockField = RecomposeScopeImplClass?.getDeclaredFieldSafe("block")

    val AbstractComposeViewClass = getClassSafe("androidx.compose.ui.platform.AbstractComposeView")
    val CompositionField = AbstractComposeViewClass?.getDeclaredFieldSafe("composition")

    val OwnerField = WrappedCompositionClass?.getDeclaredFieldSafe("owner")

    val AndroidComposeViewClass = getClassSafe("androidx.compose.ui.platform.AndroidComposeView")
    val SemanticsOwner = AndroidComposeViewClass?.getDeclaredFieldSafe("semanticsOwner")

    val TextStringSimpleElement = getClassSafe("androidx.compose.foundation.text.modifiers.TextStringSimpleElement")
    val ColorProducerField = TextStringSimpleElement?.getDeclaredFieldSafe("color")

    val BackgroundElementClass = getClassSafe("androidx.compose.foundation.BackgroundElement")
    val ColorField = BackgroundElementClass?.getDeclaredFieldSafe("color")
    val ShapeField = BackgroundElementClass?.getDeclaredFieldSafe("shape")
}

internal fun Field.accessible(): Field {
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
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Unable to get field $name through reflection, target is null" },
            e
        )
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
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            {
                "Unable to get class $className through reflection, " +
                    "either because of obfuscation or dependency version mismatch"
            },
            e
        )
        null
    }
}

@Suppress("TooGenericExceptionCaught")
internal fun Class<*>.getDeclaredFieldSafe(fieldName: String): Field? {
    return try {
        getDeclaredField(fieldName).accessible()
    } catch (e: SecurityException) {
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            {
                "Unable to get field $fieldName through reflection"
            },
            e
        )
        null
    } catch (e: NullPointerException) {
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            {
                "Unable to get field $fieldName through reflection, name is null"
            },
            e
        )
        null
    } catch (e: NoSuchFieldException) {
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            {
                "Unable to get field $fieldName through reflection, " +
                    "either because of obfuscation or dependency version mismatch"
            },
            e
        )
        null
    }
}
