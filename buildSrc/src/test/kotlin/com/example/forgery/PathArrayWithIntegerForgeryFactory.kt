/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.PathArrayWithInteger
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PathArrayWithIntegerForgeryFactory : ForgeryFactory<PathArrayWithInteger> {
    override fun getForgery(forge: Forge): PathArrayWithInteger {
        return PathArrayWithInteger(
            path = forge.aList {
                forge.anElementFrom(
                    listOf(
                        PathArrayWithInteger.Path.String(forge.aString()),
                        PathArrayWithInteger.Path.Boolean(forge.aBool()),
                        PathArrayWithInteger.Path.Point(x = forge.aLong(), y = forge.aLong()),
                        PathArrayWithInteger.Path.String("true"),
                        PathArrayWithInteger.Path.String("false"),
                        PathArrayWithInteger.Path.String("123"),
                        PathArrayWithInteger.Path.String("123.123"),
                        PathArrayWithInteger.Path.Long(forge.aLong())
                    )
                )
            }
        )
    }
}
