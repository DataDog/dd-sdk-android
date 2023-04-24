/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import android.widget.Checkable
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ViewUtils

internal abstract class CheckableWireframeMapper<T>(viewUtils: ViewUtils) :
    BaseWireframeMapper<T, MobileSegment.Wireframe>(viewUtils = viewUtils)
        where T : View, T : Checkable {

    override fun map(view: T, mappingContext: MappingContext): List<MobileSegment.Wireframe> {
        val mainWireframes = resolveMainWireframes(view, mappingContext)
        val checkableWireframes = if (view.isChecked) {
            resolveCheckedCheckable(view, mappingContext)
        } else {
            resolveNotCheckedCheckable(view, mappingContext)
        }
        checkableWireframes?.let { wireframes ->
            return mainWireframes + wireframes
        }
        return mainWireframes
    }

    abstract fun resolveMainWireframes(view: T, mappingContext: MappingContext):
        List<MobileSegment.Wireframe>

    abstract fun resolveNotCheckedCheckable(view: T, mappingContext: MappingContext):
        List<MobileSegment.Wireframe>?
    abstract fun resolveCheckedCheckable(view: T, mappingContext: MappingContext):
        List<MobileSegment.Wireframe>?
}
