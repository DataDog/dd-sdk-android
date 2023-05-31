/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.view.View
import com.datadog.android.sessionreplay.ExtensionSupport
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout

/**
 * Android Material extension support implementation to be used in the Session Replay
 * configuration.
 */
class MaterialExtensionSupport : ExtensionSupport {
    @Suppress("UNCHECKED_CAST")
    override fun getCustomViewMappers():
        Map<SessionReplayPrivacy, Map<Class<*>, WireframeMapper<View, *>>> {
        val maskUserInputSliderMapper = MaskAllSliderWireframeMapper() as WireframeMapper<View, *>
        val maskAllSliderMapper = MaskAllSliderWireframeMapper() as WireframeMapper<View, *>
        val allowAllSliderMapper = SliderWireframeMapper() as WireframeMapper<View, *>
        val maskAllTabWireframeMapper = MaskAllTabWireframeMapper() as WireframeMapper<View, *>
        val allowAllTabWireframeMapper = TabWireframeMapper() as WireframeMapper<View, *>
        return mapOf(
            SessionReplayPrivacy.ALLOW_ALL to mapOf(
                Slider::class.java to allowAllSliderMapper,
                TabLayout.TabView::class.java to allowAllTabWireframeMapper
            ),
            SessionReplayPrivacy.MASK_ALL to mapOf(
                Slider::class.java to maskAllSliderMapper,
                TabLayout.TabView::class.java to maskAllTabWireframeMapper
            ),
            SessionReplayPrivacy.MASK_USER_INPUT to mapOf(
                Slider::class.java to maskUserInputSliderMapper,
                TabLayout.TabView::class.java to allowAllTabWireframeMapper
            )
        )
    }

    override fun getOptionSelectorDetectors(): List<OptionSelectorDetector> {
        return listOf(MaterialOptionSelectorDetector())
    }
}
