/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Application
import android.os.Build
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.ActionBarContainer
import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.recorder.Recorder
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayRecorder
import com.datadog.android.sessionreplay.internal.recorder.mapper.ActionBarContainerMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckBoxMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckedTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ImageViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.NumberPickerMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.RadioButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SeekBarWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SwitchCompatMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WebViewWireframeMapper
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.android.sessionreplay.internal.time.SessionReplayTimeProvider
import com.datadog.android.sessionreplay.internal.utils.ImageViewUtils
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.recorder.mapper.EditTextMapper
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
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
    private val customMappers: List<MapperTypeWrapper<*>>,
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
    private fun builtInMappers(): List<MapperTypeWrapper<*>> {
        val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
        val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
        val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
        val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault()
        val imageViewMapper = ImageViewMapper(
            ImageViewUtils,
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )
        val textViewMapper = TextViewMapper<TextView>(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )

        val mappersList = mutableListOf(
            MapperTypeWrapper(
                SwitchCompat::class.java,
                SwitchCompatMapper(
                    textViewMapper as TextViewMapper<SwitchCompat>,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                RadioButton::class.java,
                RadioButtonMapper(
                    textViewMapper as TextViewMapper<RadioButton>,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                CheckBox::class.java,
                CheckBoxMapper(
                    textViewMapper as TextViewMapper<CheckBox>,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                CheckedTextView::class.java,
                CheckedTextViewMapper(
                    textViewMapper as TextViewMapper<CheckedTextView>,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                EditText::class.java,
                EditTextMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                Button::class.java,
                ButtonMapper(textViewMapper as TextViewMapper<Button>)
            ),
            MapperTypeWrapper(
                TextView::class.java,
                textViewMapper
            ),
            MapperTypeWrapper(
                ImageView::class.java,
                imageViewMapper
            ),
            MapperTypeWrapper(
                ActionBarContainer::class.java,
                ActionBarContainerMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                WebView::class.java,
                WebViewWireframeMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
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
    ): WireframeMapper<SeekBar>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SeekBarWireframeMapper(
                viewIdentifierResolver,
                colorStringFormatter,
                viewBoundsResolver,
                drawableToColorMapper
            )
        } else {
            null
        }
    }

    private fun getNumberPickerMapper(
        viewIdentifierResolver: ViewIdentifierResolver,
        colorStringFormatter: ColorStringFormatter,
        viewBoundsResolver: ViewBoundsResolver,
        drawableToColorMapper: DrawableToColorMapper
    ): WireframeMapper<NumberPicker>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NumberPickerMapper(
                viewIdentifierResolver,
                colorStringFormatter,
                viewBoundsResolver,
                drawableToColorMapper
            )
        } else {
            null
        }
    }
}
