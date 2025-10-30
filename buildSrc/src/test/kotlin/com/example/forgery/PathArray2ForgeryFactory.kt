/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.PathArray2
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PathArray2ForgeryFactory : ForgeryFactory<PathArray2> {
    override fun getForgery(forge: Forge): PathArray2 {
        return PathArray2(
            path = forge.aList {
                forge.anElementFrom(
                    listOf(
                        PathArray2.Path.String(forge.aString()),
                        PathArray2.Path.Boolean(forge.aBool()),
                        PathArray2.Path.Point(x = forge.aLong(), y = forge.aLong()),
                        PathArray2.Path.String("true"),
                        PathArray2.Path.String("false"),
                        PathArray2.Path.String("123"),
                        PathArray2.Path.String("123.123"),
                        PathArray2.Path.Number(forge.aNumber())
                    )
                )
            }
        )
    }
}
