/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.PathArray
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PathArrayForgeryFactory : ForgeryFactory<PathArray> {
    override fun getForgery(forge: Forge): PathArray {
        return PathArray(
            path = forge.aList {
                forge.anElementFrom(
                    listOf(
                        PathArray.Path.String(forge.aString()),
                        PathArray.Path.Boolean(forge.aBool()),
                        PathArray.Path.Point(x = forge.aLong(), y = forge.aLong()),
                        PathArray.Path.String("true"),
                        PathArray.Path.String("false"),
                        PathArray.Path.String("123"),
                        PathArray.Path.String("123.123"),
                        PathArray.Path.Long(forge.aLong())
                    )
                )
            }
        )
    }
}
