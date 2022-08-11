/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class TextWireframeForgeryFactory :
    ForgeryFactory<MobileSegment.Wireframe.TextWireframe> {
    override fun getForgery(forge: Forge): MobileSegment.Wireframe.TextWireframe {
        return MobileSegment.Wireframe.TextWireframe(
            id = forge.aPositiveLong(),
            x = forge.aPositiveLong(),
            y = forge.aPositiveLong(),
            width = forge.aPositiveLong(strict = true),
            height = forge.aPositiveLong(strict = true),
            text = forge.aString(),
            shapeStyle = forge.aNullable {
                MobileSegment.ShapeStyle(
                    forge.aStringMatching("#[0-9A-F]{6}FF"),
                    opacity = forge.aFloat(min = 0f, max = 1f),
                    cornerRadius = forge.aPositiveLong()
                )
            },
            textStyle = MobileSegment.TextStyle(
                family = forge.aString(),
                size = forge.aPositiveLong(strict = true),
                color = forge.aStringMatching("#[0-9A-F]{6}FF")
            ),
            textPosition = forge.aNullable {
                MobileSegment.TextPosition(
                    padding = forge.aNullable {
                        MobileSegment.Padding(
                            forge.aNullable { aPositiveLong() },
                            forge.aNullable { aPositiveLong() },
                            forge.aNullable { aPositiveLong() },
                            forge.aNullable { aPositiveLong() }
                        )
                    },
                    alignment = forge.aNullable {
                        MobileSegment.Alignment(
                            horizontal = forge.aNullable {
                                forge.aValueFrom(MobileSegment.Horizontal::class.java)
                            },
                            vertical = forge.aNullable {
                                forge.aValueFrom(MobileSegment.Vertical::class.java)
                            }
                        )
                    }
                )
            }
        )
    }
}
