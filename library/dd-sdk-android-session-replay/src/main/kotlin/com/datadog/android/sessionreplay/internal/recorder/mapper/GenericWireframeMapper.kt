/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.LinkedList

typealias MapperCondition = (View) -> Boolean

internal abstract class GenericWireframeMapper(
    viewWireframeMapper: ViewWireframeMapper,
    internal val imageMapper: ViewScreenshotWireframeMapper,
    textMapper: TextWireframeMapper,
    buttonMapper: ButtonMapper,
    editTextViewMapper: EditTextViewMapper,
    checkedTextViewMapper: CheckedTextViewMapper,
    decorViewMapper: DecorViewMapper,
    checkBoxMapper: CheckBoxMapper,
    radioButtonMapper: RadioButtonMapper,
    switchCompatMapper: SwitchCompatMapper,
    customMappers: Map<Class<*>, WireframeMapper<View, *>>
) : WireframeMapper<View, MobileSegment.Wireframe> {

    private val mappers: LinkedList<Pair<MapperCondition, WireframeMapper<View, *>>> = LinkedList()

    init {
        // Make sure you add the custom mappers first in the list. The order matters !!!

        customMappers.entries.forEach {
            mappers.add(defaultTypeCheckCondition(it.key) to it.value)
        }
        mappers.add(
            defaultTypeCheckCondition(SwitchCompat::class.java) to
                switchCompatMapper.toGenericMapper()
        )
        mappers.add(
            defaultTypeCheckCondition(RadioButton::class.java) to
                radioButtonMapper.toGenericMapper()
        )
        mappers.add(
            defaultTypeCheckCondition(CheckBox::class.java) to
                checkBoxMapper.toGenericMapper()
        )
        mappers.add(
            defaultTypeCheckCondition(CheckedTextView::class.java) to
                checkedTextViewMapper.toGenericMapper()
        )
        mappers.add(
            defaultTypeCheckCondition(Button::class.java) to
                buttonMapper.toGenericMapper()
        )
        mappers.add(
            defaultTypeCheckCondition(EditText::class.java) to
                editTextViewMapper.toGenericMapper()
        )
        mappers.add(
            defaultTypeCheckCondition(TextView::class.java) to
                textMapper.toGenericMapper()
        )
        mappers.add(
            defaultTypeCheckCondition(ImageView::class.java) to
                imageMapper.toGenericMapper()
        )
        mappers.add(
            decorViewCheckCondition() to
                decorViewMapper.toGenericMapper()
        )
        mappers.add(
            { _: View -> true } to
                viewWireframeMapper.toGenericMapper()
        )
    }

    override fun map(view: View, systemInformation: SystemInformation):
        List<MobileSegment.Wireframe> {
        return mappers.firstOrNull {
            it.first(view)
        }?.second?.map(view, systemInformation) ?: emptyList()
    }

    private fun defaultTypeCheckCondition(type: Class<*>): MapperCondition {
        return {
            type.isAssignableFrom(it::class.java)
        }
    }
    private fun decorViewCheckCondition(): MapperCondition {
        return {
            val viewParent = it.parent
            viewParent == null || !View::class.java.isAssignableFrom(viewParent::class.java)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun WireframeMapper<*, *>.toGenericMapper(): WireframeMapper<View, *> {
        return this as WireframeMapper<View, *>
    }
}
