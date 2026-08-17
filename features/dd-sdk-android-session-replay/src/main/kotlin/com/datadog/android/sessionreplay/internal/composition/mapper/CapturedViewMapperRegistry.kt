/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
import android.view.ViewGroup
import com.datadog.android.api.InternalLogger

/**
 * First-match-wins lookup, mirroring the legacy `TreeViewTraversal.findMapperForView` registry.
 * Falls back to [fallbackMapper] for any view with no dedicated mapper, logging telemetry once per
 * unmapped view type so unsupported views are visible without spamming logs every frame.
 */
internal class CapturedViewMapperRegistry(
    private val mappers: List<CapturedMapperTypeWrapper<*>>,
    private val fallbackMapper: CapturedViewMapper<View>,
    private val internalLogger: InternalLogger
) {
    fun resolve(view: View): CapturedViewMapper<View> {
        val mapper = mappers.firstOrNull { it.supportsView(view) }?.getUnsafeMapper()
        if (mapper != null) return mapper

        if (view !is ViewGroup) {
            val viewType = view.javaClass.canonicalName ?: view.javaClass.name
            internalLogger.log(
                level = InternalLogger.Level.INFO,
                target = InternalLogger.Target.TELEMETRY,
                messageBuilder = { "No mapper found for view $viewType" },
                throwable = null,
                onlyOnce = true,
                additionalProperties = mapOf("replay.widget.type" to viewType)
            )
        }
        return fallbackMapper
    }
}
