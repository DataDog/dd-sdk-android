/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.PathArrayWithNumber
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PathArrayWithNumberForgeryFactory : ForgeryFactory<PathArrayWithNumber> {
    override fun getForgery(forge: Forge): PathArrayWithNumber {
        return PathArrayWithNumber(
            path = forge.aList {
                forge.anElementFrom(
                    listOf(
                        PathArrayWithNumber.Path.String(forge.aString()),
                        PathArrayWithNumber.Path.Boolean(forge.aBool()),
                        PathArrayWithNumber.Path.Point(x = forge.aLong(), y = forge.aLong()),
                        PathArrayWithNumber.Path.String("true"),
                        PathArrayWithNumber.Path.String("false"),
                        PathArrayWithNumber.Path.String("123"),
                        PathArrayWithNumber.Path.String("123.123"),
                        PathArrayWithNumber.Path.Number(forge.aNumber())
                    )
                )
            }
        )
    }
}
