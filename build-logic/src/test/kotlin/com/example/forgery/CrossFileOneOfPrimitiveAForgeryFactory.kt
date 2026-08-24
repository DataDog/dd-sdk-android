/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.CrossFileOneOfPrimitiveA
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class CrossFileOneOfPrimitiveAForgeryFactory : ForgeryFactory<CrossFileOneOfPrimitiveA> {
    override fun getForgery(forge: Forge): CrossFileOneOfPrimitiveA {
        return CrossFileOneOfPrimitiveA(
            path = forge.anElementFrom(
                listOf(
                    CrossFileOneOfPrimitiveA.Path.String(forge.aString()),
                    CrossFileOneOfPrimitiveA.Path.Long(forge.aLong())
                )
            )
        )
    }
}
