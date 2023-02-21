/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

internal class MaskAllWireframeMapper(
    viewWireframeMapper: ViewWireframeMapper = ViewWireframeMapper(),
    imageMapper: ViewScreenshotWireframeMapper = ViewScreenshotWireframeMapper(viewWireframeMapper),
    textMapper: MaskAllTextWireframeMapper = MaskAllTextWireframeMapper(),
    buttonMapper: ButtonWireframeMapper = ButtonWireframeMapper(textMapper),
    editTextWireframeMapper: EditTextWireframeMapper = EditTextWireframeMapper(textMapper),
    checkedTextViewWireframeMapper: MaskAllCheckedTextViewMapper =
        MaskAllCheckedTextViewMapper(textMapper),
    decorViewWireframeMapper: DecorViewWireframeMapper =
        DecorViewWireframeMapper(viewWireframeMapper),
    checkBoxWireframeMapper: MaskAllCheckBoxWireframeMapper =
        MaskAllCheckBoxWireframeMapper(textMapper)
) : GenericWireframeMapper(
    viewWireframeMapper,
    imageMapper,
    textMapper,
    buttonMapper,
    editTextWireframeMapper,
    checkedTextViewWireframeMapper,
    decorViewWireframeMapper,
    checkBoxWireframeMapper
)
