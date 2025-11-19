/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Household
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class HouseholdForgeryFactory : ForgeryFactory<Household> {
    override fun getForgery(forge: Forge): Household {
        return Household(
            pets = forge.aNullable {
                aList {
                    anElementFrom(
                        Household.Animal.Fish(
                            water = forge.aValueFrom(Household.Water::class.java),
                            size = forge.aNullable { aPositiveLong() }
                        ),
                        Household.Animal.Bird(
                            food = forge.aValueFrom(Household.Food::class.java),
                            canFly = forge.aBool()
                        )
                    )
                }
            },
            situation = forge.anElementFrom(
                Household.Situation.Marriage(
                    spouses = forge.aList { anAlphabeticalString() }
                ),
                Household.Situation.Cotenancy(
                    roommates = forge.aList { anAlphabeticalString() }
                ),
                Household.Situation.Long(
                    item = forge.aLong()
                )
            )
        )
    }
}
