/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Application
import android.os.Build
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.Recorder
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.SessionReplayRecorder
import com.datadog.android.sessionreplay.internal.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.internal.recorder.mapper.ButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckBoxMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckedTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ImageViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MapperTypeWrapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.NumberPickerMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.RadioButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SeekBarWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SwitchCompatMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.UnsupportedViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WebViewWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.android.sessionreplay.internal.time.SessionReplayTimeProvider
import com.datadog.android.sessionreplay.internal.utils.ImageViewUtils
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal class DefaultRecorderProvider(
    private val sdkCore: FeatureSdkCore,
    private val privacy: SessionReplayPrivacy,
    private val customMappers: List<MapperTypeWrapper>,
    private val customOptionSelectorDetectors: List<OptionSelectorDetector>
) : RecorderProvider {

    override fun provideSessionReplayRecorder(
        resourceWriter: ResourcesWriter,
        recordWriter: RecordWriter,
        application: Application
    ): Recorder {
        return SessionReplayRecorder(
            application,
            resourcesWriter = resourceWriter,
            rumContextProvider = SessionReplayRumContextProvider(sdkCore),
            privacy = privacy,
            recordWriter = recordWriter,
            timeProvider = SessionReplayTimeProvider(sdkCore),
            mappers = customMappers + builtInMappers(),
            customOptionSelectorDetectors = customOptionSelectorDetectors,
            internalLogger = sdkCore.internalLogger
        )
    }

    @Suppress("LongMethod")
    private fun builtInMappers(): List<MapperTypeWrapper> {
        val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
        val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
        val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
        val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault()

        val unsupportedViewMapper = UnsupportedViewMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )
        val imageViewMapper = ImageViewMapper(
            ImageViewUtils,
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )
        val textViewMapper = TextViewMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )

        val mappersList = mutableListOf(
            MapperTypeWrapper(
                SwitchCompat::class.java,
                SwitchCompatMapper(
                    textViewMapper,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                ).toGenericMapper()
            ),
            MapperTypeWrapper(
                RadioButton::class.java,
                RadioButtonMapper(
                    textViewMapper,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                ).toGenericMapper()
            ),
            MapperTypeWrapper(
                CheckBox::class.java,
                CheckBoxMapper(
                    textViewMapper,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                ).toGenericMapper()
            ),
            MapperTypeWrapper(
                CheckedTextView::class.java,
                CheckedTextViewMapper(
                    textViewMapper,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                ).toGenericMapper()
            ),
            MapperTypeWrapper(
                Button::class.java,
                ButtonMapper(textViewMapper).toGenericMapper()
            ),
            MapperTypeWrapper(
                TextView::class.java,
                textViewMapper.toGenericMapper()
            ),
            MapperTypeWrapper(
                ImageView::class.java,
                imageViewMapper.toGenericMapper()
            ),
            MapperTypeWrapper(
                Toolbar::class.java,
                unsupportedViewMapper.toGenericMapper()
            ),
            MapperTypeWrapper(
                WebView::class.java,
                WebViewWireframeMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                ).toGenericMapper()
            )
        )

        mappersList.add(
            0,
            MapperTypeWrapper(
                android.widget.Toolbar::class.java,
                unsupportedViewMapper.toGenericMapper()
            )
        )

        getSeekBarMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )?.let {
            mappersList.add(0, MapperTypeWrapper(SeekBar::class.java, it))
        }
        getNumberPickerMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )?.let {
            mappersList.add(0, MapperTypeWrapper(NumberPicker::class.java, it))
        }
        return mappersList
    }

    private fun getSeekBarMapper(
        viewIdentifierResolver: ViewIdentifierResolver,
        colorStringFormatter: ColorStringFormatter,
        viewBoundsResolver: ViewBoundsResolver,
        drawableToColorMapper: DrawableToColorMapper
    ): WireframeMapper<View, *>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SeekBarWireframeMapper(
                viewIdentifierResolver,
                colorStringFormatter,
                viewBoundsResolver,
                drawableToColorMapper
            ).toGenericMapper()
        } else {
            null
        }
    }

    private fun getNumberPickerMapper(
        viewIdentifierResolver: ViewIdentifierResolver,
        colorStringFormatter: ColorStringFormatter,
        viewBoundsResolver: ViewBoundsResolver,
        drawableToColorMapper: DrawableToColorMapper
    ): WireframeMapper<View, *>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NumberPickerMapper(
                viewIdentifierResolver,
                colorStringFormatter,
                viewBoundsResolver,
                drawableToColorMapper
            ).toGenericMapper()
        } else {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun WireframeMapper<*, *>.toGenericMapper(): WireframeMapper<View, *> {
        return this as WireframeMapper<View, *>
    }
}
