/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.OneOfPrimitiveRef
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class OneOfPrimitiveRefForgeryFactory : ForgeryFactory<OneOfPrimitiveRef> {
    override fun getForgery(forge: Forge): OneOfPrimitiveRef {
        return OneOfPrimitiveRef(
            from = forge.aPath(),
            to = forge.aNullable { aPath() }
        )
    }

    private fun Forge.aPath(): OneOfPrimitiveRef.Path {
        return anElementFrom(
            listOf(
                OneOfPrimitiveRef.Path.String(aString()),
                OneOfPrimitiveRef.Path.Long(aLong())
            )
        )
    }
}
