/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Animal
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class AnimalForgeryFactory : ForgeryFactory<Animal> {
    override fun getForgery(forge: Forge): Animal {
        return forge.anElementFrom(
            Animal.Fish(
                water = forge.aValueFrom(Animal.Water::class.java),
                size = forge.aNullable { aPositiveLong() }
            ),
            Animal.Bird(
                food = forge.aValueFrom(Animal.Food::class.java),
                canFly = forge.aBool()
            )
        )
    }
}