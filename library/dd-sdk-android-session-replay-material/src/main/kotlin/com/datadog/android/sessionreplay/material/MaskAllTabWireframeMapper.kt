/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils
import com.google.android.material.tabs.TabLayout

internal class MaskAllTabWireframeMapper(
    viewUtils: ViewUtils = ViewUtils,
    uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
    textViewMapper: WireframeMapper<TextView, MobileSegment.Wireframe.TextWireframe> =
        MaskAllTabLabelMapper()
) : TabWireframeMapper(viewUtils, uniqueIdentifierGenerator, textViewMapper) {

    override fun resolveSelectedTabIndicatorWireframe(
        view: TabLayout.TabView,
        systemInformation: SystemInformation,
        textWireframe: MobileSegment.Wireframe.TextWireframe?
    ): MobileSegment.Wireframe? {
        return null
    }
}
