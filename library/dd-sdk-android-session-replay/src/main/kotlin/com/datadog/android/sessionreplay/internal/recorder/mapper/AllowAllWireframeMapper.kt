/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View

internal open class AllowAllWireframeMapper(
    viewWireframeMapper: ViewWireframeMapper = ViewWireframeMapper(),
    imageMapper: ViewScreenshotWireframeMapper = ViewScreenshotWireframeMapper(viewWireframeMapper),
    textMapper: TextWireframeMapper = TextWireframeMapper(),
    buttonMapper: ButtonMapper = ButtonMapper(textMapper),
    editTextViewMapper: EditTextViewMapper = EditTextViewMapper(textMapper),
    checkedTextViewMapper: CheckedTextViewMapper =
        CheckedTextViewMapper(textMapper),
    decorViewMapper: DecorViewMapper =
        DecorViewMapper(viewWireframeMapper),
    checkBoxMapper: CheckBoxMapper = CheckBoxMapper(textMapper),
    radioButtonMapper: RadioButtonMapper = RadioButtonMapper(textMapper),
    switchCompatMapper: SwitchCompatMapper = SwitchCompatMapper(textMapper),
    customMappers: Map<Class<*>, WireframeMapper<View, *>> = emptyMap()
) : GenericWireframeMapper(
    viewWireframeMapper,
    imageMapper,
    textMapper,
    buttonMapper,
    editTextViewMapper,
    checkedTextViewMapper,
    decorViewMapper,
    checkBoxMapper,
    radioButtonMapper,
    switchCompatMapper,
    customMappers
)
