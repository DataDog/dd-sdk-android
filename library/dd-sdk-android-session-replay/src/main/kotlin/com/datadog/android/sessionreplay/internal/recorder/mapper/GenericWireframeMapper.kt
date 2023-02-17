/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.model.MobileSegment

internal abstract class GenericWireframeMapper(
    private val viewWireframeMapper: ViewWireframeMapper,
    internal val imageMapper: ViewScreenshotWireframeMapper,
    private val textMapper: TextWireframeMapper,
    private val buttonMapper: ButtonWireframeMapper,
    private val editTextWireframeMapper: EditTextWireframeMapper,
    private val checkedTextViewWireframeMapper: CheckedTextViewWireframeMapper,
    private val decorViewWireframeMapper: DecorViewWireframeMapper
) : WireframeMapper<View, MobileSegment.Wireframe> {

    override fun map(view: View, systemInformation: SystemInformation):
        List<MobileSegment.Wireframe> {
        return when {
            CheckedTextView::class.java.isAssignableFrom(view::class.java) -> {
                checkedTextViewWireframeMapper.map(
                    view as CheckedTextView,
                    systemInformation
                )
            }
            Button::class.java.isAssignableFrom(view::class.java) -> {
                buttonMapper.map(view as Button, systemInformation)
            }
            EditText::class.java.isAssignableFrom(view::class.java) -> {
                editTextWireframeMapper.map(view as EditText, systemInformation)
            }
            TextView::class.java.isAssignableFrom(view::class.java) -> {
                textMapper.map(view as TextView, systemInformation)
            }
            ImageView::class.java.isAssignableFrom(view::class.java) -> {
                imageMapper.map(view as ImageView, systemInformation)
            }
            else -> {
                val viewParent = view.parent
                if (viewParent == null ||
                    !View::class.java.isAssignableFrom(viewParent::class.java)
                ) {
                    decorViewWireframeMapper.map(view, systemInformation)
                } else {
                    viewWireframeMapper.map(view, systemInformation)
                }
            }
        }
    }
}
