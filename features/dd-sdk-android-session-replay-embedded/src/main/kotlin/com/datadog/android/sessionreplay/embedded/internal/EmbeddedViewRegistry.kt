/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded.internal

import android.view.View
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Keeps track of the native [View] backing each registered embedded engine, keyed by an opaque
 * engine identity (whatever the caller uses to uniquely identify a running engine instance, e.g.
 * a Flutter engine's binary messenger).
 *
 * The engine's own SDK never needs to know the slot id: it is derived here, on demand, from the
 * exact same [View] instance the recorder mapper sees in the view tree -- guaranteeing the two
 * always agree, since both go through [DefaultViewIdentifierResolver.resolveViewId].
 *
 * Views are held weakly so a torn-down engine's view is naturally forgotten even if the caller
 * never explicitly calls [unregister].
 */
internal object EmbeddedViewRegistry {

    private val viewsByEngineKey = WeakHashMap<Any, WeakReference<View>>()

    @Synchronized
    fun register(engineKey: Any, view: View) {
        viewsByEngineKey[engineKey] = WeakReference(view)
    }

    @Synchronized
    @Suppress("UnsafeThirdPartyFunctionCall") // remove() cannot throw here
    fun unregister(engineKey: Any) {
        viewsByEngineKey.remove(engineKey)
    }

    @Synchronized
    fun resolveSlotId(engineKey: Any): String? {
        val view = viewsByEngineKey[engineKey]?.get() ?: return null
        return DefaultViewIdentifierResolver.resolveViewId(view).toString()
    }
}
