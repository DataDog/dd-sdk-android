/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class TextWireframeMutationForgeryFactory :
    ForgeryFactory<MobileSegment.WireframeUpdateMutation.TextWireframeUpdate> {
    override fun getForgery(forge: Forge):
        MobileSegment.WireframeUpdateMutation.TextWireframeUpdate {
        return MobileSegment.WireframeUpdateMutation.TextWireframeUpdate(
            id = forge.aPositiveLong(),
            x = forge.aNullable { aPositiveLong() },
            y = forge.aNullable { aPositiveLong() },
            width = forge.aNullable { aPositiveLong(strict = true) },
            height = forge.aNullable { aPositiveLong(strict = true) },
            text = forge.aNullable { aString() },
            shapeStyle = forge.aNullable {
                MobileSegment.ShapeStyle(
                    forge.aStringMatching("#[0-9A-F]{6}FF"),
                    opacity = forge.aFloat(min = 0f, max = 1f),
                    cornerRadius = forge.aPositiveLong()
                )
            },
            textStyle = forge.aNullable {
                MobileSegment.TextStyle(
                    family = forge.aString(),
                    size = forge.aPositiveLong(strict = true),
                    color = forge.aStringMatching("#[0-9A-F]{6}FF")
                )
            },
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
