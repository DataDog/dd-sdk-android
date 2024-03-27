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
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout

/**
 * Android Material extension support implementation to be used in the Session Replay
 * configuration.
 */
class MaterialExtensionSupport : ExtensionSupport {

    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
    private val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault()

    @Suppress("UNCHECKED_CAST")
    override fun getCustomViewMappers(): Map<SessionReplayPrivacy, Map<Class<*>, WireframeMapper<View, *>>> {
        val maskUserInputSliderMapper = MaskSliderWireframeMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver
        ) as WireframeMapper<View, *>
        val maskSliderMapper = MaskSliderWireframeMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver
        ) as WireframeMapper<View, *>
        val allowSliderMapper = SliderWireframeMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver
        ) as WireframeMapper<View, *>

        val maskTabWireframeMapper =
            MaskTabWireframeMapper(
                viewIdentifierResolver,
                viewBoundsResolver,
                MaskTextViewMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ) as WireframeMapper<View, *>

        val allowTabWireframeMapper =
            TabWireframeMapper(
                viewIdentifierResolver,
                viewBoundsResolver,
                TextViewMapper(viewIdentifierResolver, colorStringFormatter, viewBoundsResolver, drawableToColorMapper)
            ) as WireframeMapper<View, *>

        return mapOf(
            SessionReplayPrivacy.ALLOW to mapOf(
                Slider::class.java to allowSliderMapper,
                TabLayout.TabView::class.java to allowTabWireframeMapper
            ),
            SessionReplayPrivacy.MASK to mapOf(
                Slider::class.java to maskSliderMapper,
                TabLayout.TabView::class.java to maskTabWireframeMapper
            ),
            SessionReplayPrivacy.MASK_USER_INPUT to mapOf(
                Slider::class.java to maskUserInputSliderMapper,
                TabLayout.TabView::class.java to allowTabWireframeMapper
            )
        )
    }

    override fun getOptionSelectorDetectors(): List<OptionSelectorDetector> {
        return listOf(MaterialOptionSelectorDetector())
    }
}
