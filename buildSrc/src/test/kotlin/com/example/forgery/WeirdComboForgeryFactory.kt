/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.WeirdCombo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class WeirdComboForgeryFactory : ForgeryFactory<WeirdCombo> {
    override fun getForgery(forge: Forge): WeirdCombo {
        return WeirdCombo(
            anything = forge.anElementFrom(
                WeirdCombo.Anything.Fish(
                    water = forge.aValueFrom(WeirdCombo.Water::class.java),
                    size = forge.aNullable { aPositiveLong() }
                ),
                WeirdCombo.Anything.Bird(
                    food = forge.aValueFrom(WeirdCombo.Food::class.java),
                    canFly = forge.aBool()
                ),
                WeirdCombo.Anything.Paper(
                    title = forge.aString(),
                    author = forge.aList { aString() }
                )
            )
        )
    }
}
