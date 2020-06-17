/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Opus
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class OpusForgeryFactory : ForgeryFactory<Opus> {

    override fun getForgery(forge: Forge): Opus {
        return Opus(
            title = forge.aNullable { anAlphabeticalString() },
            composer = forge.aNullable { anAlphabeticalString() },
            artists = forge.aNullable {
                aList {
                    Opus.Artist(
                        name = forge.aNullable { anAlphabeticalString() },
                        role = forge.aNullable { getForgery() }
                    )
                }
            },
            duration = forge.aNullable { aPositiveLong() }
        )
    }
}
