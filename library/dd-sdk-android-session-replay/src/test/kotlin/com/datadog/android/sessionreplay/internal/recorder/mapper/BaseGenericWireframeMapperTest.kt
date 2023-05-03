/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.Switch
import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import org.mockito.kotlin.mock

internal open class BaseGenericWireframeMapperTest : BaseWireframeMapperTest() {

    lateinit var mockCustomMappers: Map<Class<*>, WireframeMapper<View, *>>

    lateinit var mockCustomMappedToData: List<Pair<View, List<MobileSegment.Wireframe>>>

    open fun `set up`(forge: Forge) {
        mockCustomMappedToData = listOf(
            mock<ProgressBar>() to forge.aList { getForgery() },
            mock<CheckBox>() to forge.aList { getForgery() },
            mock<Switch>() to forge.aList { getForgery() },
            mock<SwitchCompat>() to forge.aList { getForgery() },
            mock<Button>() to forge.aList { getForgery() }
        )
        mockCustomMappers = emptyMap()
    }
}
