/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.content.res.Resources
import android.view.View
import com.datadog.android.core.internal.utils.toHexString
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.ViewTarget

internal fun resolveViewTargetName(
    interactionPredicate: InteractionPredicate,
    target: ViewTarget
): String {
    return target.view?.let { view ->
        resolveTargetName(interactionPredicate, view)
    } ?: target.tag ?: ""
}

internal fun resolveTargetName(
    interactionPredicate: InteractionPredicate,
    target: Any
): String {
    val customTargetName = interactionPredicate.getTargetName(target)
    return if (!customTargetName.isNullOrEmpty()) {
        customTargetName
    } else {
        ""
    }
}

internal fun Context?.resourceIdName(id: Int): String {
    @Suppress("SwallowedException")
    return try {
        this?.resources?.getResourceEntryName(id)
            ?: idAsStringHexa(id)
    } catch (e: Resources.NotFoundException) {
        idAsStringHexa(id)
    }
}

internal fun View.targetClassName(): String {
    return this.javaClass.canonicalName ?: this.javaClass.simpleName
}

private fun idAsStringHexa(id: Int) = "0x${id.toHexString()}"
