/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskInputTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

internal class MaskInputTabWireframeMapper(
    viewUtils: ViewUtils = ViewUtils,
    uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
    textViewMapper: WireframeMapper<TextView, MobileSegment.Wireframe> =
        MaskInputTextViewMapper()
) : TabWireframeMapper(viewUtils, uniqueIdentifierGenerator, textViewMapper)
