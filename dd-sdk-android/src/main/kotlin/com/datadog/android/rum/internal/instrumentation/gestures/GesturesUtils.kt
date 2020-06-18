package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.res.Resources
import com.datadog.android.core.internal.CoreFeature

internal fun targetName(target: Any, id: String): String {
    return "${target.javaClass.simpleName}($id)"
}

internal fun resourceIdName(id: Int): String {
    @Suppress("SwallowedException")
    return try {
        CoreFeature.contextRef.get()?.resources?.getResourceEntryName(id)
            ?: idAsStringHexa(id)
    } catch (e: Resources.NotFoundException) {
        idAsStringHexa(id)
    }
}

private fun idAsStringHexa(id: Int) = "0x${id.toString(16)}"
