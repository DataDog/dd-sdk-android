/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.internal.persistence.PayloadDecoration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PayloadDecorationForgeryFactory : ForgeryFactory<PayloadDecoration> {
    override fun getForgery(forge: Forge): PayloadDecoration {
        val pair = forge.anElementFrom(
            "(" to ")",
            "{" to "}",
            "[" to "]",
            "[[" to "]]",
            "<" to ">",
            "<!--" to "-->",
            "\"" to "\"",
            "'" to "'",
            "“" to "”",
            "‘" to "’"
        )
        return forge.anElementFrom(
            PayloadDecoration(
                pair.first,
                pair.second,
                forge.anElementFrom("|", ",", ";", ":", "&", "/", ".", "-")
            )
        )
    }
}
