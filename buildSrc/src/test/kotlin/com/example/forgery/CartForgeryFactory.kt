/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Cart
import com.example.model.Veggie
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class CartForgeryFactory : ForgeryFactory<Cart> {
    override fun getForgery(forge: Forge): Cart {
        return Cart(
            fruits = forge.aNullable { aList { anAlphabeticalString() } },
            vegetables = forge.aNullable {
                aList {
                    Veggie(
                        veggieName = forge.anAlphabeticalString(),
                        veggieLike = forge.aBool()
                    )
                }
            }
        )
    }
}
