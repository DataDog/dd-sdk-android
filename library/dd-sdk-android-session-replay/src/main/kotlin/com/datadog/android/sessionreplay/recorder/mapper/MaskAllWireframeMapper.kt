/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

internal class MaskAllWireframeMapper(
    viewWireframeMapper: ViewWireframeMapper = ViewWireframeMapper(),
    imageMapper: ViewScreenshotWireframeMapper =
        ViewScreenshotWireframeMapper(viewWireframeMapper),
    textMapper: MaskAllTextWireframeMapper = MaskAllTextWireframeMapper(viewWireframeMapper),
    buttonMapper: ButtonWireframeMapper = ButtonWireframeMapper(textMapper)
) : GenericWireframeMapper(viewWireframeMapper, imageMapper, textMapper, buttonMapper)
